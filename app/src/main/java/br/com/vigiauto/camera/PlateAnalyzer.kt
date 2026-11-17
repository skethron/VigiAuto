package br.com.vigiauto.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import br.com.vigiauto.data.photos.FrameQuality
import br.com.vigiauto.data.photos.FrameRingBuffer
import br.com.vigiauto.data.photos.FrameSnapshot
import br.com.vigiauto.data.photos.VehiclePhotoCapture
import br.com.vigiauto.domain.model.PlateCandidate
import br.com.vigiauto.domain.plate.CandidateExtractor
import br.com.vigiauto.domain.plate.ConfirmationTracker
import br.com.vigiauto.domain.plate.MotionConsensusTracker
import br.com.vigiauto.domain.plate.OcrCandidateMerger
import br.com.vigiauto.ocr.OcrEngine
import br.com.vigiauto.ocr.PlateCropProcessor
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class DetectionConfig(
    val intervalMs: Long,
    val sensitivity: Float,
    val requiredConfirmations: Int,
    val motionMode: Boolean = true
)

/**
 * Analisa quadros continuamente.
 *
 * - descarta quadros quando o anterior ainda está em processamento;
 * - controla a frequência de análise;
 * - mantém os últimos quadros em um ring buffer para a fotografia;
 * - só entrega uma placa após confirmação por leituras repetidas.
 *
 * O processamento ocorre fora da thread principal e nunca bloqueia a câmera.
 */
class PlateAnalyzer(
    private val ocrEngine: OcrEngine,
    private val frameBuffer: FrameRingBuffer,
    private val tracker: ConfirmationTracker = ConfirmationTracker(),
    private val motionTracker: MotionConsensusTracker = MotionConsensusTracker(),
    private val configProvider: () -> DetectionConfig,
    private val onConfirmed: (PlateCandidate) -> Unit,
    private val onError: (String) -> Unit
) : FrameAnalyzer {

    private val busy = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vigiauto-ocr").apply { priority = Thread.NORM_PRIORITY }
    }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private var lastAnalysisAt = 0L
    @Volatile private var closed = false

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (closed) {
            image.close()
            return
        }
        val config = configProvider()
        val now = SystemClock.elapsedRealtime()

        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        if (now - lastAnalysisAt < config.intervalMs) {
            image.close()
            busy.set(false)
            return
        }
        lastAnalysisAt = now

        try {
            val width = image.width
            val height = image.height
            val rotation = image.imageInfo.rotationDegrees
            val mediaImage = image.image

            if (mediaImage == null) {
                image.close()
                busy.set(false)
                return
            }

            val input = InputImage.fromMediaImage(mediaImage, rotation)

            val started = AtomicBoolean(false)
            val finished = AtomicBoolean(false)
            val job = scope.launch {
                started.set(true)
                var upright: Bitmap? = null
                try {
                    coroutineScope {
                        val document = ocrEngine.recognize(input)
                        val firstPass = CandidateExtractor.extract(document)
                            .filter { it.confidence >= config.sensitivity }
                        if (firstPass.isEmpty()) return@coroutineScope

                        // O bitmap vertical é criado uma única vez por quadro,
                        // somente quando há texto de placa para refinar ou
                        // fotografar. Antes, todo quadro era convertido para
                        // NV21 mesmo sem nenhuma leitura.
                        upright = withContext(Dispatchers.Default) {
                            runCatching { image.toUprightBitmap() }.getOrNull()
                        }
                        val bitmap = upright

                        val candidates = if (bitmap != null) {
                            refineCandidates(bitmap, firstPass, config.sensitivity)
                        } else {
                            firstPass
                        }
                        if (candidates.isEmpty()) return@coroutineScope

                        val distinctCount = candidates.map { it.normalized }.distinct().size
                        val confirmed = mutableListOf<PlateCandidate>()

                        // Percorre todos os candidatos do quadro. Antes o laço
                        // parava na primeira placa, então uma segunda placa lida
                        // no mesmo quadro nunca era consultada.
                        for (candidate in candidates) {
                            if (tracker.offer(candidate.normalized, config.requiredConfirmations)) {
                                motionTracker.markConfirmed(candidate.normalized)
                                confirmed.add(candidate)
                                continue
                            }

                            if (config.motionMode) {
                                val consensus = motionTracker.offer(candidate.normalized)
                                if (consensus != null) {
                                    confirmed.add(
                                        candidate.copy(
                                            rawText = consensus,
                                            normalized = consensus,
                                            confidence = maxOf(candidate.confidence, 0.85f)
                                        )
                                    )
                                }
                            }
                        }
                        if (confirmed.isEmpty()) return@coroutineScope

                        val captures = confirmed.map { candidate ->
                            val capture = bitmap?.let { source ->
                                withContext(Dispatchers.Default) {
                                    runCatching {
                                        VehiclePhotoCapture.create(
                                            source = source,
                                            plateBox = candidate.box,
                                            capturedAt = System.currentTimeMillis(),
                                            candidatesInFrame = distinctCount,
                                            detectorConfidence = candidate.confidence,
                                            ocrConfidence = candidate.confidence
                                        )
                                    }.getOrNull()
                                }
                            }
                            candidate.copy(
                                candidatesInFrame = distinctCount,
                                capture = capture
                            )
                        }

                        // A conversão NV21 só é necessária na contingência em
                        // que o recorte não pôde ser gerado a partir do bitmap.
                        if (captures.any { it.capture == null }) {
                            offerFallbackFrame(image, width, height, rotation)
                        }
                        captures.forEach(onConfirmed)
                    }
                } catch (error: Throwable) {
                    onError(error.message ?: "Falha ao processar quadro.")
                } finally {
                    upright?.let { runCatching { it.recycle() } }
                    runCatching { image.close() }
                    busy.set(false)
                    finished.set(true)
                }
            }
            job.invokeOnCompletion {
                if (!started.get() && finished.compareAndSet(false, true)) {
                    runCatching { image.close() }
                    busy.set(false)
                }
            }
        } catch (error: Throwable) {
            runCatching { image.close() }
            busy.set(false)
            onError(error.message ?: "Falha ao preparar quadro para OCR.")
        }
    }

    /**
     * Segunda passada de OCR em um recorte ampliado da placa.
     *
     * O primeiro OCR enxerga o quadro inteiro. Quando ele já encontrou uma
     * placa, repetimos a leitura apenas naquela região, com mais resolução e
     * contraste. Isso melhora detalhes como a perna do Q e a abertura do C/G,
     * que costumam desaparecer em 1280×720.
     */
    private suspend fun refineCandidates(
        upright: Bitmap,
        original: List<PlateCandidate>,
        sensitivity: Float
    ): List<PlateCandidate> {
        val anchor = original.firstOrNull { it.box.width >= 16f && it.box.height >= 8f }
            ?: return original
        if (!needsRefinement(anchor)) return original
        val variants = withContext(Dispatchers.Default) {
            runCatching {
                PlateCropProcessor.prepareVariants(upright, anchor.box)
            }.getOrDefault(emptyList())
        }
        if (variants.isEmpty()) return original

        var merged = original
        try {
            for ((index, bitmap) in variants.withIndex()) {
                val document = runCatching { ocrEngine.recognize(bitmap) }.getOrNull()
                    ?: continue
                val refined = CandidateExtractor.extract(document)
                val before = OcrCandidateMerger.bestPlate(merged)
                merged = OcrCandidateMerger.merge(merged, refined)
                val after = OcrCandidateMerger.bestPlate(merged)

                // Se o recorte corrigiu a placa (por exemplo O → Q ou C → G),
                // não há motivo para gastar a segunda variante.
                if (before != null && after != null && before != after) break

                // Sem mudança na versão colorida: tenta a versão em cinza com
                // contraste, que pode revelar a perna do Q ou a barra do G.
                if (index == variants.lastIndex) break
            }
        } finally {
            variants.forEach { runCatching { it.recycle() } }
        }

        return merged.filter { it.confidence >= sensitivity }
    }

    /**
     * A segunda passada custa um ou dois OCRs extras. Ela só vale a pena
     * quando a leitura está fraca ou contém caracteres em que o recorte
     * ampliado realmente ajuda (Q, C, G e O).
     */
    private fun needsRefinement(candidate: PlateCandidate): Boolean {
        if (candidate.confidence < 0.90f) return true
        return candidate.normalized.any { it == 'Q' || it == 'C' || it == 'G' || it == 'O' }
    }

    /**
     * Última contingência para a fotografia: guarda o quadro atual no ring
     * buffer apenas quando o recorte direto do bitmap falhou. É a única
     * situação em que o caminho ML Kit converte o YUV para NV21.
     */
    private fun offerFallbackFrame(
        image: ImageProxy,
        width: Int,
        height: Int,
        rotation: Int
    ) {
        runCatching {
            val nv21 = YuvUtils.toNv21(image)
            frameBuffer.clear()
            frameBuffer.offer(
                FrameSnapshot(
                    nv21 = nv21,
                    width = width,
                    height = height,
                    rotationDegrees = rotation,
                    timestamp = System.currentTimeMillis(),
                    sharpness = FrameQuality.sharpness(nv21, width, height)
                )
            )
        }
    }

    override fun resetConfirmation() {
        tracker.reset()
        motionTracker.reset()
    }

    override fun close() {
        closed = true
        scope.cancel()
        executor.shutdown()
    }
}
