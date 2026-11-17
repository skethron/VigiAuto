package br.com.vigiauto.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.domain.model.AnalysisResolution
import br.com.vigiauto.domain.model.DetectionEngine
import br.com.vigiauto.domain.model.LookupMode
import br.com.vigiauto.domain.model.ThemeMode
import br.com.vigiauto.ui.StatusBarAppearance
import br.com.vigiauto.ui.components.SectionCard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsageBytes.collectAsStateWithLifecycle()
    val govBrLoginState by viewModel.govBrLoginState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(notice) { notice?.let { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.refreshStorageUsage() }

    var confirmDeleteHistory by remember { mutableStateOf(false) }
    var confirmDeletePhotos by remember { mutableStateOf(false) }
    var showGovBrLogin by remember { mutableStateOf(false) }

    var baseUrlField by remember(settings.httpBaseUrl) { mutableStateOf(settings.httpBaseUrl) }
    var pathField by remember(settings.httpPath) { mutableStateOf(settings.httpPath) }
    var paramField by remember(settings.httpPlateParam) { mutableStateOf(settings.httpPlateParam) }
    var tokenField by remember { mutableStateOf("") }
    var sinespTokenField by remember { mutableStateOf("") }
    var sinespGeoField by remember(settings.sinespGeo) { mutableStateOf(settings.sinespGeo) }
    var manualPlate by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportToUri) }

    StatusBarAppearance()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "Leitura e reconhecimento") {
                SwitchSetting(
                    label = "Leitura ativa",
                    description = "Inicia ou pausa o reconhecimento de placas",
                    checked = settings.readingEnabled,
                    onChange = viewModel::setReadingEnabled
                )
                SwitchSetting(
                    label = "Detector visual de placas (YOLO + CCT)",
                    description = "Localiza a placa antes do OCR e lê somente o recorte. Reduz falsos positivos em fachadas e letreiros; usa modelos locais e não envia imagens. Desligue para usar o pipeline ML Kit anterior.",
                    checked = settings.detectionEngine == DetectionEngine.ONNX,
                    onChange = { enabled ->
                        viewModel.setDetectionEngine(
                            if (enabled) DetectionEngine.ONNX else DetectionEngine.ML_KIT
                        )
                    }
                )
                SliderSetting(
                    label = "Sensibilidade do reconhecimento",
                    value = settings.sensitivity,
                    range = 0.5f..0.95f,
                    valueLabel = "%.2f".format(settings.sensitivity)
                ) { viewModel.setSensitivity(it) }
                SliderSetting(
                    label = "Frequência de análise (quadros/s)",
                    value = settings.analysisFps.toFloat(),
                    range = 1f..15f,
                    valueLabel = "${settings.analysisFps}"
                ) { viewModel.setAnalysisFps(it.toInt()) }
                Text(
                    text = "Resolução da análise",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnalysisResolution.entries.forEach { resolution ->
                        FilterChip(
                            selected = settings.analysisResolution == resolution,
                            onClick = { viewModel.setAnalysisResolution(resolution) },
                            label = {
                                Text(
                                    when (resolution) {
                                        AnalysisResolution.FAST -> "Rápida"
                                        AnalysisResolution.BALANCED -> "Equilibrada"
                                        AnalysisResolution.HIGH -> "Alta"
                                    }
                                )
                            }
                        )
                    }
                }
                Text(
                    text = "${settings.analysisResolution.width}×${settings.analysisResolution.height} px. " +
                        "Resoluções menores aceleram o OCR; maiores ajudam em placas distantes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StepperSetting(
                    label = "Confirmações antes de consultar",
                    value = settings.requiredConfirmations,
                    min = 1,
                    max = 5,
                    step = 1,
                    suffix = "leitura(s)",
                    onChange = viewModel::setRequiredConfirmations
                )
                SwitchSetting(
                    label = "Modo moto (tolerância a movimento)",
                    description = "Leituras fortes são confirmadas em 2 quadros e leituras oscilantes exigem 3+ observações. Use suporte fixo e não manuseie o aparelho em movimento.",
                    checked = settings.motionMode,
                    onChange = viewModel::setMotionMode
                )
                SwitchSetting(
                    label = "Estabilização da câmera",
                    description = "Solicita OIS/EIS ao aparelho quando suportado. Ajuda com vibração da moto e reduz fotos tremidas.",
                    checked = settings.cameraStabilization,
                    onChange = viewModel::setCameraStabilization
                )
                StepperSetting(
                    label = "Intervalo mínimo entre consultas repetidas",
                    value = settings.minRepeatQueryMinutes,
                    min = 0,
                    max = 240,
                    step = 5,
                    suffix = "min",
                    onChange = viewModel::setMinRepeatQueryMinutes
                )
            }

            SectionCard(title = "Alertas e pop ups") {
                SwitchSetting(
                    label = "Exibir pop up dos veículos",
                    checked = settings.popupEnabled,
                    onChange = viewModel::setPopupEnabled
                )
                SliderSetting(
                    label = "Duração do pop up",
                    value = settings.popupDurationSeconds.toFloat(),
                    range = 3f..20f,
                    valueLabel = "${settings.popupDurationSeconds}s"
                ) { viewModel.setPopupDurationSeconds(it.toInt()) }
                StepperSetting(
                    label = "Suprimir alertas repetidos da mesma placa",
                    value = settings.duplicateAlertSuppressionSeconds,
                    min = 10,
                    max = 300,
                    step = 10,
                    suffix = "s",
                    onChange = viewModel::setDuplicateAlertSuppressionSeconds
                )
                SwitchSetting(
                    label = "Vibrar ao reconhecer placa",
                    checked = settings.vibrationEnabled,
                    onChange = viewModel::setVibrationEnabled
                )
            }

            SectionCard(title = "Armazenamento") {
                StepperSetting(
                    label = "Máximo de registros",
                    value = settings.maxRecords,
                    min = 100,
                    max = 10_000,
                    step = 100,
                    suffix = "registros",
                    onChange = viewModel::setMaxRecords
                )
                StepperSetting(
                    label = "Limite de fotografias",
                    value = settings.photoStorageLimitMb,
                    min = 100,
                    max = 2_000,
                    step = 100,
                    suffix = "MB",
                    onChange = viewModel::setPhotoStorageLimitMb
                )
                StepperSetting(
                    label = "Retenção do histórico",
                    value = settings.retentionDays,
                    min = 0,
                    max = 365,
                    step = 30,
                    suffix = if (settings.retentionDays == 0) "sem limite" else "dias",
                    onChange = viewModel::setRetentionDays
                )
                Text(
                    text = "Fotografias em uso: ${formatMegabytes(storageUsage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("vigiauto-export.json") }) {
                        Text("Exportar registros (JSON)")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmDeletePhotos = true }) {
                        Text("Excluir fotografias")
                    }
                    OutlinedButton(onClick = { confirmDeleteHistory = true }) {
                        Text("Excluir histórico")
                    }
                }
            }

            SectionCard(title = "Integração com o serviço de consulta") {
                Text(
                    text = when (settings.lookupMode) {
                        LookupMode.REMOTE ->
                            "Consulta Simples: modo alternativo. A URL e o token são obtidos do " +
                                "remote config oficial em tempo de execução; nenhuma credencial " +
                                "fica embutida no APK. O uso está sujeito aos limites e termos do provedor."
                        LookupMode.SINESP ->
                            "Modo padrão desde a 1.5.0: consulta real ao Sinesp v1 com o seu token pessoal " +
                                "do gov.br. É o protocolo do aplicativo oficial, não uma API pública; " +
                                "use apenas para veículos que você está vendo, sem consulta em massa."
                        LookupMode.HTTP ->
                            "Modo manual para endpoints alternativos autorizados. Preencha URL, caminho, " +
                                "parâmetro e token; o contrato deve ser validado por você."
                        LookupMode.DEMO ->
                            "Modo demonstração: todos os dados são fictícios e nenhuma consulta externa é feita."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LookupMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.lookupMode == mode,
                            onClick = { viewModel.setLookupMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        LookupMode.REMOTE -> "Consulta Simples"
                                        LookupMode.SINESP -> "Sinesp (padrão)"
                                        LookupMode.HTTP -> "HTTP manual"
                                        LookupMode.DEMO -> "Demo"
                                    }
                                )
                            }
                        )
                    }
                }

                when (settings.lookupMode) {
                    LookupMode.REMOTE -> {
                        Text(
                            text = "Endpoint: placa.costaw.com.br (HTTPS). A configuração é renovada " +
                                "automaticamente; se o token expirar, a próxima consulta busca a versão atual.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { viewModel.testConnection() }) {
                            Text("Testar conexão")
                        }
                    }

                    LookupMode.SINESP -> {
                        Text(
                            text = "Este é o modo padrão do app. Entre com a sua conta gov.br pelo " +
                                "botão abaixo ou cole um token já obtido. O token fica cifrado pelo " +
                                "Android Keystore, expira e precisa ser renovado. Sem token, as " +
                                "leituras usam a Consulta Simples automaticamente.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                viewModel.resetGovBrLogin()
                                showGovBrLogin = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Login gov.br")
                        }
                        Text(
                            text = "Abre o site oficial do gov.br dentro do app. O VigiAuto não " +
                                "vê sua senha; recebe apenas o código de retorno e o troca pelo token.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Se preferir, cole um token já obtido:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = sinespTokenField,
                            onValueChange = { sinespTokenField = it },
                            label = { Text("Token do gov.br (sem Bearer)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = {
                            sinespTokenField = clipboard.getText()?.text?.trim().orEmpty()
                        }) {
                            Text("Colar token da área de transferência")
                        }
                        Text(
                            text = if (settings.hasSinespToken) {
                                "Token armazenado com criptografia."
                            } else {
                                "Nenhum token configurado; as leituras usam a Consulta Simples."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = sinespGeoField,
                            onValueChange = { sinespGeoField = it },
                            label = { Text("Localização (longitude,latitude)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "O padrão é um ponto neutro em Brasília. O app não acessa o GPS " +
                                "neste modo; a localização é apenas a informada acima.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                viewModel.saveSinesp(sinespTokenField, sinespGeoField)
                                if (sinespTokenField.isNotBlank()) sinespTokenField = ""
                            }) { Text("Salvar") }
                            TextButton(
                                onClick = { viewModel.removeSinespToken() },
                                enabled = settings.hasSinespToken
                            ) { Text("Remover token") }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testConnection() },
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(56.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Verificar\nconfiguração",
                                    style = MaterialTheme.typography.labelMedium,
                                    lineHeight = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Text(
                                text = "Confere o token salvo e a localização; não gasta consulta.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "Sem token, a leitura consulta automaticamente pela Consulta " +
                                "Simples. Ao fazer login ou salvar um token, o modo Sinesp é ativado.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LookupMode.HTTP -> {
                        OutlinedTextField(
                            value = baseUrlField,
                            onValueChange = { baseUrlField = it },
                            label = { Text("URL base (HTTPS)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pathField,
                            onValueChange = { pathField = it },
                            label = { Text("Caminho do endpoint") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = paramField,
                            onValueChange = { paramField = it },
                            label = { Text("Nome do parâmetro da placa") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tokenField,
                            onValueChange = { tokenField = it },
                            label = { Text("Token de acesso (opcional)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (settings.hasHttpToken) {
                                "Token armazenado com criptografia."
                            } else {
                                "Nenhum token configurado."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                viewModel.saveIntegration(baseUrlField, pathField, paramField)
                                if (tokenField.isNotBlank()) {
                                    viewModel.saveToken(tokenField)
                                    tokenField = ""
                                }
                            }) { Text("Salvar integração") }
                            OutlinedButton(onClick = { viewModel.testConnection() }) {
                                Text("Testar conexão")
                            }
                            TextButton(
                                onClick = { viewModel.saveToken("") },
                                enabled = settings.hasHttpToken
                            ) {
                                Text("Remover token")
                            }
                        }
                    }

                    LookupMode.DEMO -> {
                        Text(
                            text = "Nenhuma consulta real será realizada. Use este modo apenas para " +
                                "demonstração e testes de interface.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                connectionTest?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionCard(title = "Consulta manual") {
                OutlinedTextField(
                    value = manualPlate,
                    onValueChange = { manualPlate = it.uppercase(Locale.ROOT) },
                    label = { Text("Placa (ABC1234 ou ABC1D23)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.manualQuery(manualPlate)
                        manualPlate = ""
                    },
                    enabled = manualPlate.isNotBlank()
                ) { Text("Consultar placa") }
                Text(
                    text = "Para uma nova fotografia manual, abra os detalhes do veículo no histórico.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "Aparência") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Sistema"
                                        ThemeMode.LIGHT -> "Claro"
                                        ThemeMode.DARK -> "Escuro"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            SectionCard(title = "Privacidade") {
                Text(
                    text = "As placas e fotografias ficam somente neste aparelho, no armazenamento privado do aplicativo. " +
                        "As imagens completas da câmera não são enviadas ao serviço de consulta — apenas a placa é enviada. " +
                        "No modo Sinesp v1, a placa e a localização configurada (ponto neutro por padrão) são enviadas " +
                        "ao serviço; o token pessoal do gov.br fica cifrado no aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmDeleteHistory) {
        AlertDialog(
            onDismissRequest = { confirmDeleteHistory = false },
            title = { Text("Excluir histórico?") },
            text = { Text("Todos os registros e fotografias serão apagados permanentemente.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHistory()
                    confirmDeleteHistory = false
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteHistory = false }) { Text("Cancelar") }
            }
        )
    }

    if (confirmDeletePhotos) {
        AlertDialog(
            onDismissRequest = { confirmDeletePhotos = false },
            title = { Text("Excluir fotografias?") },
            text = { Text("As fotografias serão apagadas, e os registros das placas serão mantidos.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhotos()
                    confirmDeletePhotos = false
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePhotos = false }) { Text("Cancelar") }
            }
        )
    }

    if (showGovBrLogin) {
        GovBrLoginDialog(
            state = govBrLoginState,
            onCode = viewModel::onGovBrCode,
            onResetState = viewModel::resetGovBrLogin,
            onDismiss = {
                showGovBrLogin = false
                viewModel.resetGovBrLogin()
            }
        )
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            onValueChangeFinished = { onChange(local) }
        )
    }
}

@Composable
private fun StepperSetting(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    suffix: String,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$value $suffix",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Diminuir")
        }
        IconButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Aumentar")
        }
    }
}

private fun formatMegabytes(bytes: Long?): String {
    if (bytes == null) return "calculando…"
    val megabytes = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(megabytes)
}
