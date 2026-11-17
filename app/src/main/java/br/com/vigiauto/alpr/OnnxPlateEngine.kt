package br.com.vigiauto.alpr

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import br.com.vigiauto.domain.plate.PlateBox
import br.com.vigiauto.domain.plate.PlateDetection
import br.com.vigiauto.domain.plate.PlateNormalizer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Detector visual e OCR especializado, executados inteiramente no aparelho.
 *
 * YOLOv9 localiza as regiões de placa; o CCT lê somente esses recortes. Nenhum
 * quadro é enviado para a rede. O código de integração segue o projeto de
 * referência `detector-placas-android` (fast-alpr, MIT); a licença dos pesos
 * ONNX precisa ser confirmada antes de distribuição comercial.
 */
class OnnxPlateEngine(assets: AssetManager) : Closeable {

    companion object {
        private const val DETECTOR_SIZE = 384
        private const val OCR_WIDTH = 128
        private const val OCR_HEIGHT = 64
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
        private const val PAD = '_'
        private const val PAD_COLOR = 114

        /** Caixas abaixo disso nem chegam ao OCR. */
        const val DETECTOR_SCORE_MIN = 0.30f

        /**
         * Menor confiança de detector que ainda pode ser aceita, considerando
         * a sensibilidade mínima de 0.30 (0.38 + (0.30 - 0.5) * 0.25 = 0.33).
         * Caixas abaixo disso seriam descartadas depois; não gastam OCR.
         */
        private const val OCR_DETECTOR_SCORE_MIN = 0.33f

        /**
         * Três caixas cobrem a sequência de três veículos que antes perdia a
         * placa do meio. O OCR é limitado para não comprometer a taxa de
         * quadros em leituras com várias faixas.
         */
        private const val MAX_OCR_BOXES = 3
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val detectorSession: OrtSession
    private val ocrSession: OrtSession
    private val ocrFallbackSession: OrtSession
    private val detectorInput: String
    private val detectorOutput: String
    private val ocrInput: String
    private val ocrOutput: String
    private val ocrFallbackInput: String
    private val ocrFallbackOutput: String

    // Buffers e bitmaps reutilizados. O engine é serializado por @Synchronized,
    // então não há concorrência entre quadros.
    private val detectorBitmap = Bitmap.createBitmap(
        DETECTOR_SIZE,
        DETECTOR_SIZE,
        Bitmap.Config.ARGB_8888
    )
    private val detectorCanvas = Canvas(detectorBitmap)
    private val detectorPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val detectorPixels = IntArray(DETECTOR_SIZE * DETECTOR_SIZE)
    private val detectorDestination = RectF()
    private val detectorBuffer = ByteBuffer
        .allocateDirect(3 * DETECTOR_SIZE * DETECTOR_SIZE * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val ocrBitmap = Bitmap.createBitmap(OCR_WIDTH, OCR_HEIGHT, Bitmap.Config.ARGB_8888)
    private val ocrCanvas = Canvas(ocrBitmap)
    private val ocrPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ocrPixels = IntArray(OCR_WIDTH * OCR_HEIGHT)
    private val ocrSource = Rect()
    private val ocrDestination = Rect(0, 0, OCR_WIDTH, OCR_HEIGHT)
    private val ocrBytes = ByteArray(OCR_WIDTH * OCR_HEIGHT * 3)
    private val ocrBuffer = ByteBuffer
        .allocateDirect(ocrBytes.size)
        .order(ByteOrder.nativeOrder())

    init {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val detectorBytes = assets.open("models/detector.onnx").use { it.readBytes() }
        detectorSession = environment.createSession(detectorBytes, options)
        detectorInput = detectorSession.inputNames.first()
        detectorOutput = detectorSession.outputNames.first()

        val ocrBytes = assets.open("models/ocr.onnx").use { it.readBytes() }
        ocrSession = environment.createSession(ocrBytes, options)
        ocrInput = ocrSession.inputNames.first()
        ocrOutput = ocrSession.outputNames.firstOrNull { it == "plate" }
            ?: ocrSession.outputNames.first()

        // O modelo novo acerta placas que o anterior errava, mas pode devolver
        // uma sequência inválida ou um caractere fraco em recortes difíceis.
        // O modelo anterior é a segunda opinião nesses casos; leituras válidas
        // e confiantes seguem sem uma inferência extra.
        val fallbackBytes = assets.open("models/ocr_fallback.onnx").use { it.readBytes() }
        ocrFallbackSession = environment.createSession(fallbackBytes, options)
        ocrFallbackInput = ocrFallbackSession.inputNames.first()
        ocrFallbackOutput = ocrFallbackSession.outputNames.firstOrNull { it == "plate" }
            ?: ocrFallbackSession.outputNames.first()
    }

    private data class FrameTransform(
        val scale: Float,
        val padX: Double,
        val padY: Double,
        val width: Int,
        val height: Int
    )

    /**
     * Executa o detector no bitmap já na orientação correta e devolve uma
     * entrada para cada caixa encontrada. Caixas sem leitura válida ainda são
     * descartadas aqui; o chamador recebe apenas regiões com texto.
     */
    @Synchronized
    fun detect(bitmap: Bitmap): List<PlateDetection> {
        if (bitmap.width < 16 || bitmap.height < 16) return emptyList()

        val boxes = predict(bitmap)
            .asSequence()
            .filter { it.confidence >= OCR_DETECTOR_SCORE_MIN }
            // O detector pode devolver mais de uma caixa para a mesma placa.
            // Sem o NMS, essas duplicatas consumiriam as vagas de OCR e a placa
            // do meio de uma sequência de três poderia ficar sem leitura.
            .nonMaxSuppression()
            .take(MAX_OCR_BOXES)
            .toList()
        if (boxes.isEmpty()) return emptyList()

        val results = mutableListOf<PlateDetection>()
        for (box in boxes) {
            val reading = try {
                recognize(bitmap, box)
            } catch (_: Throwable) {
                null
            } ?: continue

            results.add(
                PlateDetection(
                    box = PlateBox(
                        box.x1.toFloat(),
                        box.y1.toFloat(),
                        box.x2.toFloat(),
                        box.y2.toFloat()
                    ),
                    detectorConfidence = box.confidence,
                    rawText = reading.rawText,
                    normalized = PlateNormalizer.normalize(reading.rawText),
                    ocrConfidence = reading.confidence,
                    minCharacterConfidence = reading.minCharacterConfidence
                )
            )
        }

        return results.sortedByDescending { it.score }
    }

    private data class RawBox(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val confidence: Float
    )

    private fun Sequence<RawBox>.nonMaxSuppression(
        threshold: Float = 0.50f
    ): Sequence<RawBox> = sequence {
        val kept = mutableListOf<RawBox>()
        for (box in this@nonMaxSuppression.sortedByDescending { it.confidence }) {
            if (kept.none { it.iou(box) >= threshold }) {
                kept.add(box)
                yield(box)
            }
        }
    }

    private fun RawBox.iou(other: RawBox): Float {
        val left = maxOf(x1, other.x1)
        val top = maxOf(y1, other.y1)
        val right = minOf(x2, other.x2)
        val bottom = minOf(y2, other.y2)
        val intersection = (right - left).coerceAtLeast(0) *
            (bottom - top).coerceAtLeast(0)
        if (intersection == 0) return 0f
        val ownArea = (x2 - x1).coerceAtLeast(0) * (y2 - y1).coerceAtLeast(0)
        val otherArea = (other.x2 - other.x1).coerceAtLeast(0) *
            (other.y2 - other.y1).coerceAtLeast(0)
        val union = ownArea + otherArea - intersection
        return if (union <= 0) 0f else intersection.toFloat() / union
    }

    private fun boxOf(row: FloatArray, transform: FrameTransform): RawBox? {
        if (row.size < 7) return null
        val score = row[6]
        if (score < DETECTOR_SCORE_MIN) return null

        val x1 = ((row[1] - transform.padX) / transform.scale)
            .toInt().coerceIn(0, transform.width - 1)
        val y1 = ((row[2] - transform.padY) / transform.scale)
            .toInt().coerceIn(0, transform.height - 1)
        val x2 = ((row[3] - transform.padX) / transform.scale)
            .toInt().coerceIn(0, transform.width)
        val y2 = ((row[4] - transform.padY) / transform.scale)
            .toInt().coerceIn(0, transform.height)
        if (x2 - x1 < 8 || y2 - y1 < 4) return null

        return RawBox(x1, y1, x2, y2, score)
    }

    /**
     * Letterbox desenhado diretamente no bitmap reutilizado. Evita criar um
     * bitmap escalado e outro bitmap de entrada a cada quadro.
     */
    private fun predict(bitmap: Bitmap): List<RawBox> {
        val width = bitmap.width
        val height = bitmap.height
        val scale = min(DETECTOR_SIZE.toFloat() / height, DETECTOR_SIZE.toFloat() / width)
        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val padX = (DETECTOR_SIZE - scaledWidth) / 2.0
        val padY = (DETECTOR_SIZE - scaledHeight) / 2.0

        detectorCanvas.drawColor(Color.rgb(PAD_COLOR, PAD_COLOR, PAD_COLOR))
        detectorDestination.set(
            padX.toFloat(),
            padY.toFloat(),
            (padX + scaledWidth).toFloat(),
            (padY + scaledHeight).toFloat()
        )
        detectorCanvas.drawBitmap(bitmap, null, detectorDestination, detectorPaint)
        detectorBitmap.getPixels(
            detectorPixels,
            0,
            DETECTOR_SIZE,
            0,
            0,
            DETECTOR_SIZE,
            DETECTOR_SIZE
        )

        detectorBuffer.clear()
        // O detector espera NCHW (três planos), não RGB intercalado.
        for (channel in 0..2) {
            val shift = 16 - channel * 8
            for (pixel in detectorPixels) {
                detectorBuffer.put(((pixel shr shift) and 0xFF) / 255f)
            }
        }
        detectorBuffer.rewind()

        val transform = FrameTransform(scale, padX, padY, width, height)
        return OnnxTensor.createTensor(
            environment,
            detectorBuffer,
            longArrayOf(1, 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
        ).use { tensor ->
            detectorSession.run(mapOf(detectorInput to tensor), setOf(detectorOutput)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val rows = result.get(0).value as Array<FloatArray>
                rows.mapNotNull { row -> boxOf(row, transform) }
            }
        }.sortedByDescending { it.confidence }
    }

    /**
     * Recorta e redimensiona a caixa diretamente para o bitmap fixo do OCR.
     * O bitmap de recorte intermediário deixa de ser alocado a cada leitura.
     */
    private fun recognize(source: Bitmap, box: RawBox): OcrReading? {
        val boxWidth = box.x2 - box.x1
        val boxHeight = box.y2 - box.y1
        if (boxWidth < 4 || boxHeight < 4) return null

        ocrSource.set(box.x1, box.y1, box.x2, box.y2)
        ocrCanvas.drawBitmap(source, ocrSource, ocrDestination, ocrPaint)
        ocrBitmap.getPixels(ocrPixels, 0, OCR_WIDTH, 0, 0, OCR_WIDTH, OCR_HEIGHT)

        var position = 0
        for (pixel in ocrPixels) {
            ocrBytes[position++] = ((pixel shr 16) and 0xFF).toByte()
            ocrBytes[position++] = ((pixel shr 8) and 0xFF).toByte()
            ocrBytes[position++] = (pixel and 0xFF).toByte()
        }

        ocrBuffer.clear()
        ocrBuffer.put(ocrBytes)
        ocrBuffer.rewind()

        return OnnxTensor.createTensor(
            environment,
            ocrBuffer,
            longArrayOf(1, OCR_HEIGHT.toLong(), OCR_WIDTH.toLong(), 3),
            OnnxJavaType.UINT8
        ).use { tensor ->
            val primary = runOcr(tensor, ocrSession, ocrInput, ocrOutput)
            if (!OcrFallbackPolicy.shouldTryFallback(primary)) {
                return@use primary
            }

            val fallback = runOcr(
                tensor,
                ocrFallbackSession,
                ocrFallbackInput,
                ocrFallbackOutput
            )
            if (OcrFallbackPolicy.preferFallback(primary, fallback)) fallback else primary
        }
    }

    private fun runOcr(
        tensor: OnnxTensor,
        session: OrtSession,
        inputName: String,
        outputName: String
    ): OcrReading? = session.run(mapOf(inputName to tensor), setOf(outputName)).use { result ->
        @Suppress("UNCHECKED_CAST")
        val probabilities = (result.get(0).value as Array<Array<FloatArray>>)[0]
        decode(probabilities)
    }

    private fun decode(probabilities: Array<FloatArray>): OcrReading? {
        val text = StringBuilder()
        val characterConfidences = mutableListOf<Float>()
        for (slots in probabilities) {
            var bestIndex = 0
            var bestProbability = -1f
            for (index in slots.indices) {
                if (slots[index] > bestProbability) {
                    bestProbability = slots[index]
                    bestIndex = index
                }
            }
            if (bestIndex in ALPHABET.indices) {
                val character = ALPHABET[bestIndex]
                if (character != PAD) {
                    text.append(character)
                    characterConfidences.add(bestProbability)
                }
            }
        }

        val raw = text.toString().trim()
        if (raw.isEmpty()) return null

        val confidence = if (characterConfidences.isEmpty()) 0f
        else characterConfidences.average().toFloat()
        val minimum = characterConfidences.minOrNull() ?: 0f
        return OcrReading(raw, confidence, minimum)
    }

    @Synchronized
    override fun close() {
        runCatching { detectorSession.close() }
        runCatching { ocrSession.close() }
        runCatching { ocrFallbackSession.close() }
        runCatching { detectorBitmap.recycle() }
        runCatching { ocrBitmap.recycle() }
    }
}

internal data class OcrReading(
    val rawText: String,
    val confidence: Float,
    val minCharacterConfidence: Float
)
