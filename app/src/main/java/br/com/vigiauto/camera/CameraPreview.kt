package br.com.vigiauto.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "VigiAutoCamera"

private data class CameraAttempt(
    val analysisWidth: Int,
    val analysisHeight: Int,
    val previewWidth: Int,
    val previewHeight: Int,
    val stabilization: Boolean,
    val previewOnly: Boolean = false,
    val outputRotation: Boolean = true,
)

@Composable
fun CameraPreview(
    readingEnabled: Boolean,
    lensIndex: Int,
    analysisWidth: Int,
    analysisHeight: Int,
    stabilizationEnabled: Boolean,
    analyzer: FrameAnalyzer?,
    onLensesAvailable: (Int) -> Unit,
    onCameraStarted: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1f,
    retryKey: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewStreaming by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Toque para focar: importante principalmente no zoom 8x, quando o
        // autofoco contínuo pode escolher o fundo e deixar a placa tremida.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera, previewView) {
                    detectTapGestures { position ->
                        val activeCamera = camera ?: return@detectTapGestures
                        runCatching {
                            val point = previewView.meteringPointFactory
                                .createPoint(position.x, position.y)
                            activeCamera.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            )
                        }
                    }
                }
        )
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { state ->
            previewStreaming = state == PreviewView.StreamState.STREAMING
            if (previewStreaming) onCameraStarted()
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    // Se o bind terminou mas o preview não recebe imagens, o app antes ficava
    // eternamente em silêncio. Depois de alguns segundos mostramos o erro e
    // oferecemos o botão Reiniciar.
    LaunchedEffect(previewStreaming, readingEnabled, retryKey) {
        if (readingEnabled && !previewStreaming) {
            delay(4_500)
            if (!previewStreaming && readingEnabled) {
                onError(
                    "A câmera abriu, mas o preview não está recebendo imagens. " +
                        "Use Reiniciar para reconectar a câmera."
                )
            }
        } else if (previewStreaming) {
            onCameraStarted()
        }
    }

    LaunchedEffect(
        readingEnabled,
        lensIndex,
        analyzer,
        analysisWidth,
        analysisHeight,
        stabilizationEnabled,
        retryKey
    ) {
        try {
            previewStreaming = false
            camera = null
            val provider = awaitCameraProvider(context)
            cameraProvider = provider

            val backCameras = provider.availableCameraInfos.filter {
                it.lensFacing == CameraSelector.LENS_FACING_BACK
            }
            onLensesAvailable(backCameras.size.coerceAtLeast(1))

            val selectedSelector = buildCameraSelector(backCameras, lensIndex)
            val selectors = buildList {
                add(selectedSelector)
                if (selectedSelector != CameraSelector.DEFAULT_BACK_CAMERA) {
                    add(CameraSelector.DEFAULT_BACK_CAMERA)
                }
            }

            val analysisAttempts = buildAnalysisAttempts(
                readingEnabled = readingEnabled,
                analyzer = analyzer,
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight,
                stabilizationEnabled = stabilizationEnabled
            )
            // Se algum aparelho recusar a rotação de saída do CameraX, ainda
            // tenta a mesma análise no caminho antigo, com rotação no app.
            val legacyAnalysisAttempts = analysisAttempts.map {
                it.copy(stabilization = false, outputRotation = false)
            }
            val previewAttempts = listOf(
                CameraAttempt(0, 0, 1280, 720, stabilization = false, previewOnly = true),
                CameraAttempt(0, 0, 640, 480, stabilization = false, previewOnly = true)
            )

            // Tenta todas as combinações de análise em todas as lentes antes de
            // aceitar o modo somente-preview. Assim uma lente secundária
            // incompatível não impede a lente traseira padrão de fazer OCR.
            val candidates = buildList {
                for (selector in selectors) {
                    for (attempt in analysisAttempts) {
                        add(selector to attempt)
                    }
                }
                for (selector in selectors) {
                    for (attempt in legacyAnalysisAttempts) {
                        add(selector to attempt)
                    }
                }
                for (selector in selectors) {
                    for (attempt in previewAttempts) {
                        add(selector to attempt)
                    }
                }
            }

            camera = null
            var boundCamera: Camera? = null
            var previewOnlyFallback = false
            var lastFailure: Throwable? = null

            for ((selector, attempt) in candidates) {
                try {
                    provider.unbindAll()
                    val preview = buildPreview(previewView, attempt)

                    if (attempt.previewOnly || analyzer == null) {
                        boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview
                        )
                        previewOnlyFallback = readingEnabled && analyzer != null
                    } else {
                        val analysis = buildAnalysis(
                            attempt,
                            previewView.display?.rotation ?: 0
                        )
                        analysis.setAnalyzer(analysisExecutor, analyzer)
                        boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            analysis
                        )
                    }
                    break
                } catch (error: Throwable) {
                    lastFailure = error
                    Log.w(
                        TAG,
                        "Falha ao abrir câmera (lente=$lensIndex, " +
                            "análise=${attempt.analysisWidth}x${attempt.analysisHeight}, " +
                            "preview=${attempt.previewWidth}x${attempt.previewHeight}, " +
                            "estabilização=${attempt.stabilization}); tentando próxima combinação",
                        error
                    )
                }
            }

            if (boundCamera == null) {
                onError(cameraErrorMessage(lastFailure))
                return@LaunchedEffect
            }

            camera = boundCamera
            onCameraStarted()
            if (previewOnlyFallback) {
                onError(
                    "A câmera abriu, mas este aparelho não suporta o modo de leitura " +
                        "atual. Reduza a resolução de análise nas configurações."
                )
            }
        } catch (cancelled: CancellationException) {
            // A saída do escopo da composição é uma troca normal de ciclo de
            // vida, não uma falha da câmera. Não deixe um banner vermelho
            // permanente quando o preview já está funcionando.
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "Falha ao iniciar a câmera", error)
            onError(cameraErrorMessage(error))
        }
    }

    LaunchedEffect(camera, zoomRatio, readingEnabled) {
        val activeCamera = camera ?: return@LaunchedEffect
        val maxZoom = activeCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: zoomRatio
        runCatching {
            activeCamera.cameraControl.setZoomRatio(zoomRatio.coerceIn(1f, maxZoom))
        }
        // Recentraliza o foco depois da troca de zoom ou de uma reconexão.
        delay(350)
        runCatching {
            if (previewView.width > 0 && previewView.height > 0) {
                val point = previewView.meteringPointFactory.createPoint(
                    previewView.width / 2f,
                    previewView.height / 2f
                )
                activeCamera.cameraControl.startFocusAndMetering(
                    FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }
}

private fun buildCameraSelector(
    backCameras: List<CameraInfo>,
    lensIndex: Int
): CameraSelector {
    if (backCameras.size <= 1 || lensIndex <= 0) {
        return CameraSelector.DEFAULT_BACK_CAMERA
    }
    val target = backCameras.getOrNull(lensIndex.coerceIn(0, backCameras.lastIndex))
        ?: return CameraSelector.DEFAULT_BACK_CAMERA
    return CameraSelector.Builder()
        .addCameraFilter { infos -> infos.filter { it == target } }
        .build()
}

private fun buildAnalysisAttempts(
    readingEnabled: Boolean,
    analyzer: FrameAnalyzer?,
    analysisWidth: Int,
    analysisHeight: Int,
    stabilizationEnabled: Boolean
): List<CameraAttempt> {
    if (!readingEnabled || analyzer == null) {
        return emptyList()
    }

    return buildList {
        if (stabilizationEnabled) {
            add(CameraAttempt(analysisWidth, analysisHeight, 1280, 720, true))
        }
        add(CameraAttempt(analysisWidth, analysisHeight, 1280, 720, false))
        if (analysisWidth > 960 || analysisHeight > 540) {
            add(CameraAttempt(960, 540, 1280, 720, false))
        }
        add(CameraAttempt(640, 480, 1280, 720, false))
        add(CameraAttempt(640, 480, 640, 480, false))
    }.distinct()
}

@SuppressLint("UnsafeOptInUsageError")
private fun buildPreview(previewView: PreviewView, attempt: CameraAttempt): Preview {
    val builder = Preview.Builder()
        .setResolutionSelector(resolutionSelector(attempt.previewWidth, attempt.previewHeight))

    if (attempt.stabilization) {
        runCatching { builder.setPreviewStabilizationEnabled(true) }
            .onFailure { Log.w(TAG, "Estabilização de preview indisponível", it) }
        // Além da estabilização de preview do CameraX, pedimos estabilização
        // de vídeo ao Camera2. Em aparelhos que não suportam, o build/bind
        // falha e o próximo candidato sem estabilização é tentado.
        runCatching {
            Camera2Interop.Extender(builder).setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
        }.onFailure { Log.w(TAG, "Estabilização de vídeo indisponível", it) }
    }

    return builder.build().also { preview ->
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun buildAnalysis(attempt: CameraAttempt, targetRotation: Int): ImageAnalysis {
    val builder = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector(attempt.analysisWidth, attempt.analysisHeight))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setTargetRotation(targetRotation)
    if (attempt.outputRotation) {
        // O CameraX entrega o buffer já na orientação da tela. Isso elimina
        // a criação de um segundo bitmap girado a cada quadro no analisador.
        builder.setOutputImageRotationEnabled(true)
    }
    if (attempt.stabilization) {
        runCatching {
            Camera2Interop.Extender(builder).setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
        }.onFailure { Log.w(TAG, "Estabilização no fluxo de análise indisponível", it) }
    }
    return builder.build()
}

private fun resolutionSelector(width: Int, height: Int): ResolutionSelector =
    ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(width, height),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()

private fun cameraErrorMessage(error: Throwable?): String {
    val detail = error?.message.orEmpty()
    return if (detail.contains("surface combination", ignoreCase = true)) {
        "Este aparelho não suporta a combinação de câmera usada pelo app. " +
            "Abra as configurações e escolha uma resolução de análise menor."
    } else {
        "Falha ao iniciar a câmera: ${detail.ifBlank { "erro desconhecido" }}"
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (error: java.util.concurrent.ExecutionException) {
                    continuation.resumeWithException(error.cause ?: error)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
