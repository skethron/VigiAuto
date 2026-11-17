package br.com.vigiauto.ui.main

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.vigiauto.VigiAutoApp
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.data.prefs.AppSettings
import br.com.vigiauto.domain.model.DetectionEngine
import br.com.vigiauto.domain.model.PlateCandidate
import br.com.vigiauto.domain.plate.PlateNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PopupItem(
    val recordId: Long,
    val displayPlate: String,
    val detectionTimestamp: Long,
    val isNew: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container: AppContainer = (application as VigiAutoApp).container
    private val repository = container.repository
    private val settings = container.settings

    val appSettings: StateFlow<AppSettings> = settings.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val uniqueCount: StateFlow<Int> = repository.uniqueCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingCount: StateFlow<Int> = repository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val online: StateFlow<Boolean> = repository.online
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _lensCount = MutableStateFlow(1)
    val lensCount: StateFlow<Int> = _lensCount.asStateFlow()

    private val _currentPopup = MutableStateFlow<PopupItem?>(null)
    val currentPopup: StateFlow<PopupItem?> = _currentPopup.asStateFlow()

    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError: StateFlow<String?> = _cameraError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val popupQueue = ArrayDeque<PopupItem>()
    private var popupJob: Job? = null
    private var noticeJob: Job? = null
    private val lastPopupAt = HashMap<String, Long>()

    fun setReading(active: Boolean) {
        viewModelScope.launch { settings.setReadingEnabled(active) }
    }

    fun onLensesAvailable(count: Int) {
        _lensCount.value = count.coerceAtLeast(1)
    }

    fun cycleLens() {
        viewModelScope.launch {
            val count = _lensCount.value.coerceAtLeast(1)
            val next = (appSettings.value.lastCameraLensIndex + 1) % count
            settings.setLastCameraLensIndex(next)
        }
    }

    fun onPlateConfirmed(candidate: PlateCandidate) {
        viewModelScope.launch {
            val result = repository.recordDetection(candidate) ?: return@launch
            val config = appSettings.value

            if (config.vibrationEnabled) vibrate()

            if (!config.popupEnabled) return@launch

            val now = System.currentTimeMillis()
            val lastShown = lastPopupAt[result.normalizedPlate] ?: 0L
            val suppressionMs = config.duplicateAlertSuppressionSeconds * 1_000L
            if (!result.isNew && now - lastShown < suppressionMs) return@launch
            lastPopupAt[result.normalizedPlate] = now
            // A consulta pode corrigir a placa para uma hipótese ambígua.
            // Silencia também as variantes para não exibir o mesmo veículo
            // duas vezes durante a janela de supressão.
            PlateNormalizer.alternatives(result.normalizedPlate).forEach { alternative ->
                lastPopupAt[alternative] = now
            }

            enqueuePopup(
                PopupItem(
                    recordId = result.id,
                    displayPlate = result.displayPlate,
                    detectionTimestamp = result.timestamp,
                    isNew = result.isNew
                )
            )
        }
    }

    fun dismissPopup() {
        popupJob?.cancel()
        popupJob = null
        _currentPopup.value = null
        showNextPopup()
    }

    fun reportError(message: String) {
        _cameraError.value = message
    }

    fun clearError() {
        _cameraError.value = null
    }

    /**
     * O detector ONNX é opcional e pode falhar em aparelhos/ABIs não
     * suportados. Não deixe o usuário sem leitura: volta ao ML Kit uma vez.
     */
    fun fallbackToMlKit() {
        viewModelScope.launch {
            if (appSettings.value.detectionEngine == DetectionEngine.ONNX) {
                settings.setDetectionEngine(DetectionEngine.ML_KIT)
                showNotice("Detector visual indisponível; usando ML Kit.")
            }
        }
    }

    fun showNotice(message: String) {
        _notice.value = message
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(4_000)
            _notice.value = null
        }
    }

    fun dismissNotice() {
        noticeJob?.cancel()
        _notice.value = null
    }

    private fun enqueuePopup(item: PopupItem) {
        popupQueue.addLast(item)
        if (_currentPopup.value == null) showNextPopup()
    }

    private fun showNextPopup() {
        popupJob?.cancel()
        val next = popupQueue.removeFirstOrNull()
        _currentPopup.value = next
        if (next != null) {
            popupJob = viewModelScope.launch {
                delay(appSettings.value.popupDurationSeconds * 1_000L)
                _currentPopup.value = null
                showNextPopup()
            }
        }
    }

    private fun vibrate() {
        runCatching {
            val vibrator = getApplication<Application>().getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
