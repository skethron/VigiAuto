package br.com.vigiauto.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import br.com.vigiauto.alpr.OnnxPlateEngine
import br.com.vigiauto.data.photos.FrameQuality
import br.com.vigiauto.data.photos.FrameRingBuffer
import br.com.vigiauto.data.photos.FrameSnapshot
import br.com.vigiauto.data.photos.VehiclePhotoCapture
import br.com.vigiauto.domain.model.PlateCandidate
import br.com.vigiauto.domain.plate.CandidateExtractor
import br.com.vigiauto.domain.plate.PlateDetection
import br.com.vigiauto.domain.plate.PlateNormalizer
import br.com.vigiauto.domain.plate.PlatePattern
import br.com.vigiauto.domain.plate.PlateTrackTracker
import br.com.vigiauto.ocr.OcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Pipeline detector-primeiro:
 *
 * quadro → YOLOv9 (caixa da placa) → recorte → CCT → validação → tracking.
 *
 * O ML Kit não percorre mais o quadro inteiro. Ele é usado apenas como segunda
 * opinião no recorte quando o CCT devolve texto inválido ou com baixa
 * confiança. Isso elimina a principal fonte de falsos positivos do pipeline
 * anterior (letreiros e textos comuns interpretados como placa).
 */
class OnnxPlateAnalyzer(
    private val engine: OnnxPlateEngine,
    private val ocrEngine: OcrEngine,
    private val frameBuffer: FrameRingBuffer,
    private val configProvider: () -> DetectionConfig,
    private val onConfirmed: (PlateCandidate) -> Unit,
    private val onError: (String) -> Unit,
    private val onFatalError: () -> Unit = {}
) : FrameAnalyzer {

    private val busy = AtomicBoolean(false)
    private val tracker = PlateTrackTracker()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vigiauto-alpr").apply { priority = Thread.NORM_PRIORITY }
    }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private var lastAnalysisAt = 0L
    private var lastErrorAt = 0L
    private var consecutiveFailures = 0
    @Volatile private var closed = false

    @ExperimentalGetImage
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

        val width = image.width
        val height = image.height
        val rotation = image.imageInfo.rotationDegrees

        val started = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val job = scope.launch {
            started.set(true)
            var upright: Bitmap? = null
            try {
                val bitmap = withContext(Dispatchers.Default) { image.toUprightBitmap() }
                upright = bitmap

                val detections = withContext(Dispatchers.Default) { engine.detect(bitmap) }

                val accepted = detections.mapNotNull { detection ->
                    refine(detection, bitmap, config)
                }.filter { it.isAccepted(config) }
                    .dedupe()

                val confirmations = tracker.update(
                    detections = accepted,
                    now = System.currentTimeMillis(),
                    requiredConfirmations = config.requiredConfirmations,
                    motionMode = config.motionMode
                )

                // A fotografia é preparada a partir do mesmo bitmap vertical
                // que confirmou a leitura. A conversão NV21 só entra como
                // contingência se o recorte direto falhar; antes, todo quadro
                // confirmado pagava uma conversão inteira sem necessidade.
                val outputs = confirmations.mapNotNull { confirmation ->
                    val detection = confirmation.detection
                    val normalized = detection.normalized ?: return@mapNotNull null
                    val textMismatch = PlateNormalizer.normalize(detection.rawText)
                        ?.let { it != normalized }
                        ?: true
                    val capture = withContext(Dispatchers.Default) {
                        runCatching {
                            VehiclePhotoCapture.create(
                                source = bitmap,
                                plateBox = detection.box,
                                capturedAt = System.currentTimeMillis(),
                                candidatesInFrame = confirmation.candidatesInFrame,
                                detectorConfidence = detection.detectorConfidence,
                                ocrConfidence = detection.ocrConfidence,
                                textMismatch = textMismatch
                            )
                        }.getOrNull()
                    }
                    PlateCandidate(
                        rawText = detection.rawText,
                        normalized = normalized,
                        confidence = detection.score,
                        box = detection.box,
                        lineCount = if (detection.box.width / detection.box.height < 1.5f) 2 else 1,
                        candidatesInFrame = confirmation.candidatesInFrame,
                        detectorConfidence = detection.detectorConfidence,
                        ocrConfidence = detection.ocrConfidence,
                        capture = capture
                    )
                }

                if (outputs.any { it.capture == null }) {
                    val frame = withContext(Dispatchers.Default) {
                        runCatching {
                            val nv21 = YuvUtils.toNv21(image)
                            FrameSnapshot(
                                nv21 = nv21,
                                width = width,
                                height = height,
                                rotationDegrees = rotation,
                                timestamp = System.currentTimeMillis(),
                                sharpness = FrameQuality.sharpness(nv21, width, height)
                            )
                        }.getOrNull()
                    }
                    frameBuffer.clear()
                    if (frame != null) frameBuffer.offer(frame)
                }

                consecutiveFailures = 0
                outputs.forEach(onConfirmed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                consecutiveFailures += 1
                val elapsed = SystemClock.elapsedRealtime()
                if (elapsed - lastErrorAt > 3_000L) {
                    lastErrorAt = elapsed
                    onError(error.message ?: "Falha no detector visual de placas.")
                }
                if (consecutiveFailures >= 5) {
                    consecutiveFailures = 0
                    onFatalError()
                }
            } finally {
                upright?.let { runCatching { it.recycle() } }
                runCatching { image.close() }
                busy.set(false)
                finished.set(true)
            }
        }
        // Se o escopo for cancelado entre o compareAndSet e o dispatch do
        // launch, o corpo nunca executa e o finally acima não roda. Sem este
        // fallback, busy ficaria true, o ImageProxy aberto e a câmera pararia
        // de entregar quadros para sempre.
        job.invokeOnCompletion {
            if (!started.get() && finished.compareAndSet(false, true)) {
                runCatching { image.close() }
                busy.set(false)
            }
        }
    }

    /**
     * Segunda opinião apenas no recorte da placa. Nunca usa texto do quadro
     * inteiro. Se o CCT já leu uma placa válida com confiança razoável, a
     * leitura especializada é preservada para não trocar a placa por uma
     * hipótese visual equivalente.
     */
    private suspend fun refine(
        detection: PlateDetection,
        bitmap: Bitmap,
        config: DetectionConfig
    ): PlateDetection {
        if (detection.normalized != null &&
            detection.ocrConfidence >= 0.55f &&
            detection.minCharacterConfidence >= 0.30f
        ) {
            return detection
        }

        val left = detection.box.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = detection.box.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = detection.box.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = detection.box.bottom.toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 8 || bottom - top < 4) return detection

        val crop = runCatching {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }.getOrNull() ?: return detection

        val candidate = try {
            val document = runCatching { ocrEngine.recognize(crop) }.getOrNull()
            document?.let { CandidateExtractor.extract(it) }
                ?.filter { it.confidence >= config.sensitivity * 0.75f }
                ?.maxByOrNull { it.confidence }
        } finally {
            if (crop !== bitmap) crop.recycle()
        } ?: return detection

        val normalized = candidate.normalized
        if (!PlatePattern.isPlate(normalized)) return detection
        if (detection.normalized != null &&
            !PlateNormalizer.isVisuallyEquivalent(detection.normalized, normalized)
        ) {
            return detection
        }

        return detection.copy(
            rawText = candidate.rawText,
            normalized = normalized,
            ocrConfidence = max(detection.ocrConfidence, candidate.confidence * 0.90f),
            minCharacterConfidence = max(detection.minCharacterConfidence, 0.45f)
        )
    }

    private fun PlateDetection.isAccepted(config: DetectionConfig): Boolean {
        val normalized = normalized ?: return false
        if (!PlatePattern.isPlate(normalized)) return false

        val detectorMinimum = (0.38f + (config.sensitivity - 0.5f) * 0.25f)
            .coerceIn(0.38f, 0.49f)
        val ocrMinimum = (0.48f + (config.sensitivity - 0.5f) * 0.35f)
            .coerceIn(0.48f, 0.64f)
        if (detectorConfidence < detectorMinimum) return false
        if (ocrConfidence < ocrMinimum) return false
        if (score < config.sensitivity * 0.78f) return false

        val ratio = box.width / box.height
        if (ratio < 0.8f || ratio > 6.0f) return false
        return true
    }

    private fun List<PlateDetection>.dedupe(): List<PlateDetection> {
        val kept = mutableListOf<PlateDetection>()
        for (detection in sortedByDescending { it.score }) {
            val duplicate = kept.any {
                it.normalized == detection.normalized && it.box.iou(detection.box) >= 0.50f
            }
            if (!duplicate) kept.add(detection)
        }
        return kept
    }

    override fun resetConfirmation() {
        tracker.reset()
    }

    override fun close() {
        closed = true
        scope.cancel()
        executor.shutdown()
    }
}
