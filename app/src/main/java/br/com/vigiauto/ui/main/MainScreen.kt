package br.com.vigiauto.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import br.com.vigiauto.alpr.OnnxPlateEngine
import br.com.vigiauto.camera.CameraPreview
import br.com.vigiauto.camera.DetectionConfig
import br.com.vigiauto.camera.FrameAnalyzer
import br.com.vigiauto.camera.OnnxPlateAnalyzer
import br.com.vigiauto.camera.PlateAnalyzer
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.domain.model.DetectionEngine
import br.com.vigiauto.domain.model.LookupMode
import br.com.vigiauto.ocr.OcrEngine
import br.com.vigiauto.ui.StatusBarAppearance
import br.com.vigiauto.ui.theme.Amber
import br.com.vigiauto.ui.theme.BlueAccent
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    container: AppContainer,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetails: (Long) -> Unit
) {
    val viewModel: MainViewModel = viewModel()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val uniqueCount by viewModel.uniqueCount.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val lensCount by viewModel.lensCount.collectAsStateWithLifecycle()
    val popup by viewModel.currentPopup.collectAsStateWithLifecycle()
    val cameraError by viewModel.cameraError.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var zoomEight by remember { mutableStateOf(false) }
    var cameraRetryKey by remember { mutableIntStateOf(0) }
    var lastAutoRetryError by remember { mutableStateOf<String?>(null) }
    var wasPaused by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) viewModel.showNotice("A leitura precisa da permissão de câmera.")
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result.values.any { it }
        if (!hasLocationPermission) {
            viewModel.showNotice(
                "Sem localização autorizada, o histórico não poderá mostrar o endereço da consulta."
            )
        }
    }

    // A localização é opcional e não bloqueia a câmera. Pedimos depois da
    // permissão de câmera para não empilhar dois diálogos ao mesmo tempo.
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && !hasLocationPermission) {
            delay(900)
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Uma falha transitória de câmera (outro app usando o sensor, tela
    // bloqueada, sessão fechada pelo sistema) ganha uma tentativa automática.
    // Se o mesmo erro persistir, o usuário pode usar o botão Reiniciar do
    // banner sem entrar em um loop infinito de rebind.
    LaunchedEffect(cameraError) {
        if (cameraError == null) {
            lastAutoRetryError = null
        } else if (lastAutoRetryError != cameraError) {
            lastAutoRetryError = cameraError
            delay(1_800)
            cameraRetryKey++
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // ON_STOP, e não ON_PAUSE: diálogos de permissão e o chooser
                // pausam a Activity sem encerrar a câmera; não queremos um
                // rebind desnecessário nesses casos.
                Lifecycle.Event.ON_STOP -> wasPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    hasLocationPermission =
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                    // Ao voltar de outra tela/app a sessão da câmera pode ter
                    // sido encerrada pelo sistema. Um rebind explícito evita o
                    // preview preto ou o estado "câmera indisponível".
                    if (wasPaused && hasCameraPermission) {
                        wasPaused = false
                        cameraRetryKey++
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    StatusBarAppearance(darkIcons = false)
    KeepScreenOn()

    val ocrEngine = remember { OcrEngine() }
    DisposableEffect(Unit) { onDispose { ocrEngine.close() } }

    val onnxEngine = remember(settings.detectionEngine) {
        if (settings.detectionEngine == DetectionEngine.ONNX) {
            runCatching { OnnxPlateEngine(context.assets) }.getOrNull()
        } else {
            null
        }
    }
    DisposableEffect(onnxEngine) { onDispose { onnxEngine?.close() } }

    LaunchedEffect(settings.detectionEngine, onnxEngine) {
        if (settings.detectionEngine == DetectionEngine.ONNX && onnxEngine == null) {
            viewModel.showNotice("Detector visual indisponível; usando ML Kit.")
        }
    }

    val settingsState = rememberUpdatedState(settings)
    val analyzer: FrameAnalyzer? = remember(
        settings.readingEnabled,
        hasCameraPermission,
        settings.detectionEngine,
        onnxEngine
    ) {
        if (settings.readingEnabled && hasCameraPermission) {
            val configProvider = {
                val current = settingsState.value
                DetectionConfig(
                    intervalMs = current.analysisIntervalMs,
                    sensitivity = current.sensitivity,
                    requiredConfirmations = current.requiredConfirmations,
                    motionMode = current.motionMode
                )
            }
            if (settings.detectionEngine == DetectionEngine.ONNX && onnxEngine != null) {
                OnnxPlateAnalyzer(
                    engine = onnxEngine,
                    ocrEngine = ocrEngine,
                    frameBuffer = container.frameBuffer,
                    configProvider = configProvider,
                    onConfirmed = { candidate -> viewModel.onPlateConfirmed(candidate) },
                    onError = { message -> viewModel.reportError(message) },
                    onFatalError = viewModel::fallbackToMlKit
                )
            } else {
                PlateAnalyzer(
                    ocrEngine = ocrEngine,
                    frameBuffer = container.frameBuffer,
                    configProvider = configProvider,
                    onConfirmed = { candidate -> viewModel.onPlateConfirmed(candidate) },
                    onError = { message -> viewModel.reportError(message) }
                )
            }
        } else {
            null
        }
    }
    DisposableEffect(analyzer) { onDispose { analyzer?.close() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (hasCameraPermission) {
            CameraPreview(
                readingEnabled = settings.readingEnabled,
                lensIndex = settings.lastCameraLensIndex,
                analysisWidth = settings.analysisResolution.width,
                analysisHeight = settings.analysisResolution.height,
                stabilizationEnabled = settings.cameraStabilization,
                analyzer = analyzer,
                onLensesAvailable = viewModel::onLensesAvailable,
                onCameraStarted = viewModel::clearError,
                onError = viewModel::reportError,
                zoomRatio = if (zoomEight) 8f else 1f,
                retryKey = cameraRetryKey,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PermissionPlaceholder(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            )
        }

        PlateGuideOverlay(modifier = Modifier.fillMaxSize())

        TopStatusBar(
            readingActive = settings.readingEnabled,
            uniqueCount = uniqueCount,
            pendingCount = pendingCount,
            online = online,
            lookupMode = settings.lookupMode,
            hasSinespToken = settings.hasSinespToken,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (settings.lookupMode == LookupMode.SINESP && !settings.hasSinespToken) {
                SinespTokenBanner(onOpenSettings = onOpenSettings)
            }

            cameraError?.let { message ->
                MessageBanner(
                    text = message,
                    containerColor = Color(0xCC7A1F1F),
                    onDismiss = { viewModel.clearError() },
                    actionLabel = "Reiniciar",
                    onAction = {
                        lastAutoRetryError = null
                        cameraRetryKey++
                        viewModel.clearError()
                    }
                )
            }

            notice?.let { message ->
                MessageBanner(
                    text = message,
                    containerColor = Color(0xCC0E2A52),
                    onDismiss = viewModel::dismissNotice
                )
            }

            popup?.let { item ->
                PlatePopupCard(
                    popup = item,
                    container = container,
                    onOpenDetails = { id ->
                        viewModel.dismissPopup()
                        onOpenDetails(id)
                    },
                    onDismiss = { viewModel.dismissPopup() }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.setReading(!settings.readingEnabled) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(
                        imageVector = if (settings.readingEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (settings.readingEnabled) "Pausar" else "Iniciar",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (lensCount > 1) {
                    FilledTonalIconButton(
                        onClick = { viewModel.cycleLens() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Trocar lente")
                    }
                }

                FilledTonalIconButton(
                    onClick = { zoomEight = !zoomEight },
                    modifier = Modifier.size(56.dp),
                    colors = if (zoomEight) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = BlueAccent,
                            contentColor = Color.White
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    }
                ) {
                    Text(
                        text = "8x",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                BadgedBox(
                    badge = {
                        if (uniqueCount > 0) {
                            Badge { Text(uniqueCount.toString()) }
                        }
                    }
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = "Histórico de veículos")
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun TopStatusBar(
    readingActive: Boolean,
    uniqueCount: Int,
    pendingCount: Int,
    online: Boolean,
    lookupMode: LookupMode,
    hasSinespToken: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xB3101316)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (readingActive) BlueAccent else Amber, RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (readingActive) "Leitura ativa" else "Leitura pausada",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (online) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = if (online) "Conectado" else "Sem conexão",
                    tint = if (online) BlueAccent else Amber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Configurações",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$uniqueCount placas únicas • $pendingCount consultas pendentes",
                color = Color(0xFFD7DEE8),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (lookupMode) {
                    LookupMode.REMOTE -> "Consulta Simples"
                    LookupMode.SINESP -> if (hasSinespToken) {
                        "Sinesp (token gov.br)"
                    } else {
                        "Sinesp sem token • Consulta Simples"
                    }
                    LookupMode.HTTP -> "Integração HTTP manual"
                    LookupMode.DEMO -> "Demonstração: dados fictícios"
                },
                color = when (lookupMode) {
                    LookupMode.REMOTE -> BlueAccent
                    LookupMode.SINESP -> if (hasSinespToken) Color(0xFF9FD8FF) else Amber
                    LookupMode.HTTP -> Color(0xFFD7DEE8)
                    LookupMode.DEMO -> Amber
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PlateGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guideWidth = size.width * 0.84f
        val guideHeight = size.height * 0.16f
        val left = (size.width - guideWidth) / 2f
        val top = (size.height - guideHeight) / 2f
        val stroke = 3.dp.toPx()
        val corner = 18.dp.toPx()

        drawRoundRect(
            color = Color(0x66FFFFFF),
            topLeft = Offset(left, top),
            size = Size(guideWidth, guideHeight),
            cornerRadius = CornerRadius(corner),
            style = Stroke(width = stroke)
        )

        val accent = BlueAccent.copy(alpha = 0.95f)
        val arm = 26.dp.toPx()
        val positions = listOf(
            Offset(left, top),
            Offset(left + guideWidth, top),
            Offset(left, top + guideHeight),
            Offset(left + guideWidth, top + guideHeight)
        )
        val directions = listOf(
            Pair(1f, 1f), Pair(-1f, 1f), Pair(1f, -1f), Pair(-1f, -1f)
        )
        positions.forEachIndexed { index, position ->
            val (dx, dy) = directions[index]
            drawLine(
                color = accent,
                start = position,
                end = Offset(position.x + arm * dx, position.y),
                strokeWidth = stroke * 1.6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent,
                start = position,
                end = Offset(position.x, position.y + arm * dy),
                strokeWidth = stroke * 1.6f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SinespTokenBanner(onOpenSettings: () -> Unit) {
    Surface(
        color = Color(0xCC7A5A12),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sinesp sem token • Consulta Simples",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "As leituras usam a Consulta Simples. Faça login gov.br em Ajustes para ativar o Sinesp.",
                    color = Color(0xFFFFE0B2),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "Abrir",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MessageBanner(
    text: String,
    containerColor: Color,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = BlueAccent,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "Permissão de câmera necessária",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "O VigiAuto usa a câmera traseira apenas para reconhecer placas de veículos.",
                color = Color(0xFFB9C2D0),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequest) { Text("Conceder permissão") }
            OutlinedButton(onClick = onOpenSettings) { Text("Abrir configurações do app") }
        }
    }
}
