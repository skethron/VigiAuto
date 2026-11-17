package br.com.vigiauto.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.vigiauto.VigiAutoApp
import br.com.vigiauto.data.lookup.SinespLoginResult
import br.com.vigiauto.data.lookup.SinespV1Protocol
import br.com.vigiauto.data.prefs.AppSettings
import br.com.vigiauto.data.prefs.SettingsRepository
import br.com.vigiauto.data.repo.VehicleRepository
import br.com.vigiauto.domain.model.AnalysisResolution
import br.com.vigiauto.domain.model.DetectionEngine
import br.com.vigiauto.domain.model.LookupMode
import br.com.vigiauto.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GovBrLoginState {
    data object Idle : GovBrLoginState
    data object Exchanging : GovBrLoginState
    data class Success(
        val expiresAtEpochMs: Long?,
        val warning: String?
    ) : GovBrLoginState

    data class Error(val message: String) : GovBrLoginState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VigiAutoApp).container
    private val repository: VehicleRepository = container.repository
    private val settings: SettingsRepository = container.settings

    val appSettings: StateFlow<AppSettings> = settings.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _connectionTest = MutableStateFlow<String?>(null)
    val connectionTest: StateFlow<String?> = _connectionTest.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _govBrLoginState = MutableStateFlow<GovBrLoginState>(GovBrLoginState.Idle)
    val govBrLoginState: StateFlow<GovBrLoginState> = _govBrLoginState.asStateFlow()

    private var govBrLoginJob: Job? = null

    private val _storageUsageBytes = MutableStateFlow<Long?>(null)
    val storageUsageBytes: StateFlow<Long?> = _storageUsageBytes.asStateFlow()

    fun setReadingEnabled(value: Boolean) = launchSetting { settings.setReadingEnabled(value) }
    fun setDetectionEngine(value: DetectionEngine) = launchSetting {
        settings.setDetectionEngine(value)
    }
    fun setSensitivity(value: Float) = launchSetting { settings.setSensitivity(value) }
    fun setAnalysisFps(value: Int) = launchSetting { settings.setAnalysisFps(value) }
    fun setAnalysisResolution(value: AnalysisResolution) = launchSetting {
        settings.setAnalysisResolution(value)
    }
    fun setRequiredConfirmations(value: Int) = launchSetting { settings.setRequiredConfirmations(value) }
    fun setMotionMode(value: Boolean) = launchSetting { settings.setMotionMode(value) }
    fun setCameraStabilization(value: Boolean) = launchSetting {
        settings.setCameraStabilization(value)
    }
    fun setMinRepeatQueryMinutes(value: Int) = launchSetting { settings.setMinRepeatQueryMinutes(value) }
    fun setPopupDurationSeconds(value: Int) = launchSetting { settings.setPopupDurationSeconds(value) }
    fun setDuplicateAlertSuppressionSeconds(value: Int) = launchSetting {
        settings.setDuplicateAlertSuppressionSeconds(value)
    }
    fun setPopupEnabled(value: Boolean) = launchSetting { settings.setPopupEnabled(value) }
    fun setVibrationEnabled(value: Boolean) = launchSetting { settings.setVibrationEnabled(value) }
    fun setMaxRecords(value: Int) = launchSetting { settings.setMaxRecords(value) }
    fun setPhotoStorageLimitMb(value: Int) = launchSetting { settings.setPhotoStorageLimitMb(value) }
    fun setRetentionDays(value: Int) = launchSetting { settings.setRetentionDays(value) }
    fun setThemeMode(value: ThemeMode) = launchSetting { settings.setThemeMode(value) }
    fun setLookupMode(value: LookupMode) = launchSetting { settings.setLookupMode(value) }
    fun setHttpPath(value: String) = launchSetting { settings.setHttpPath(value) }
    fun setHttpPlateParam(value: String) = launchSetting { settings.setHttpPlateParam(value) }

    fun saveIntegration(baseUrl: String, path: String, plateParam: String) {
        viewModelScope.launch {
            settings.setHttpBaseUrl(baseUrl)
            settings.setHttpPath(path)
            settings.setHttpPlateParam(plateParam)
            showNotice("Configuração de integração salva.")
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            settings.setHttpToken(token)
            showNotice(if (token.isBlank()) "Token removido." else "Token armazenado com criptografia do Android Keystore.")
        }
    }

    /**
     * Salva a localização e, se o campo de token não estiver vazio, substitui
     * o token do Sinesp. Um campo vazio mantém o token atual; para apagá-lo
     * existe a ação explícita [removeSinespToken]. Ao salvar um token novo, o
     * modo Sinesp é ativado para que a próxima placa já use a consulta real.
     */
    fun saveSinesp(token: String, geo: String) {
        viewModelScope.launch {
            val normalizedGeo = SinespV1Protocol.normalizeGeo(geo)
            if (normalizedGeo == null) {
                showNotice("Localização inválida. Use longitude,latitude (ex.: -47.8825,-15.7942).")
                return@launch
            }

            val normalizedToken = if (token.isBlank()) {
                null
            } else {
                SinespV1Protocol.normalizeToken(token)
                    ?: run {
                        showNotice("Token inválido. Cole o token do gov.br sem o prefixo Bearer.")
                        return@launch
                    }
            }

            settings.setSinespGeo(normalizedGeo)
            if (normalizedToken != null) {
                settings.setSinespToken(normalizedToken)
                settings.setLookupMode(LookupMode.SINESP)
            }
            showNotice(
                if (normalizedToken != null) {
                    "Token e localização salvos. Modo Sinesp ativado; o token fica criptografado no aparelho."
                } else {
                    "Localização do Sinesp salva."
                }
            )
        }
    }

    fun removeSinespToken() {
        viewModelScope.launch {
            settings.setSinespToken(null)
            showNotice("Token do Sinesp removido. As leituras voltam a usar a Consulta Simples.")
        }
    }

    fun resetGovBrLogin() {
        govBrLoginJob?.cancel()
        govBrLoginJob = null
        _govBrLoginState.value = GovBrLoginState.Idle
    }

    /**
     * Recebe o `code` de uso único do WebView (ou da URL colada no fallback),
     * troca pelo token no SINESP e o salva cifrado. O WebView nunca recebe o
     * token; só o backend do SINESP e este aplicativo.
     */
    fun onGovBrCode(code: String) {
        if (code.isBlank()) return
        if (govBrLoginJob?.isActive == true) return
        if (_govBrLoginState.value is GovBrLoginState.Success) return

        _govBrLoginState.value = GovBrLoginState.Exchanging
        govBrLoginJob = viewModelScope.launch {
            when (val result = container.sinespAuth.exchangeCode(code)) {
                is SinespLoginResult.Success -> {
                    runCatching {
                        settings.setSinespToken(result.token)
                        settings.setLookupMode(LookupMode.SINESP)
                    }.onSuccess {
                        _govBrLoginState.value = GovBrLoginState.Success(
                            expiresAtEpochMs = result.expiresAtEpochMs,
                            warning = result.warning
                        )
                    }.onFailure {
                        _govBrLoginState.value = GovBrLoginState.Error(
                            "O login foi concluído, mas não foi possível salvar o token no aparelho. " +
                                "Tente novamente."
                        )
                    }
                }

                is SinespLoginResult.Failure -> {
                    _govBrLoginState.value = GovBrLoginState.Error(result.message)
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch { runConnectionTest() }
    }

    private suspend fun runConnectionTest() {
        _connectionTest.value = "Testando conexão…"
        val result = runCatching { container.lookupRouter.testConnection() }
            .getOrElse { br.com.vigiauto.data.lookup.ConnectionTestResult(false, it.message ?: "erro") }
        _connectionTest.value = (if (result.ok) "OK — " else "Falha — ") + result.detail
    }

    fun manualQuery(plate: String) {
        viewModelScope.launch {
            val result = repository.manualQuery(plate)
            showNotice(result.message)
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageUsageBytes.value = repository.storageUsageBytes()
        }
    }

    fun deleteHistory() {
        viewModelScope.launch {
            repository.deleteAllRecords(deletePhotos = true)
            refreshStorageUsage()
            showNotice("Histórico apagado.")
        }
    }

    fun deletePhotos() {
        viewModelScope.launch {
            repository.deleteAllPhotos()
            refreshStorageUsage()
            showNotice("Fotografias apagadas.")
        }
    }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = repository.exportRecordsJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Não foi possível abrir o destino.")
                }
            }.onSuccess {
                showNotice("Exportação concluída.")
            }.onFailure {
                showNotice("Falha ao exportar: ${it.message ?: "erro desconhecido"}")
            }
        }
    }

    fun showNotice(message: String) {
        viewModelScope.launch {
            _notice.value = message
            delay(3_500)
            if (_notice.value == message) _notice.value = null
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
