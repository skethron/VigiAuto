package br.com.vigiauto.ui.details

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.vigiauto.VigiAutoApp
import br.com.vigiauto.data.photos.PhotoDownloadResult
import br.com.vigiauto.data.photos.PhotoExporter
import br.com.vigiauto.data.repo.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class SharePayload(
    val uri: Uri,
    val subject: String
)

class DetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VigiAutoApp).container
    private val repository: VehicleRepository = container.repository
    private val photoExporter = PhotoExporter(application)
    private val shareCardGenerator = ShareCardGenerator(application)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _sharePayload = MutableStateFlow<SharePayload?>(null)
    val sharePayload: StateFlow<SharePayload?> = _sharePayload.asStateFlow()

    fun requery(recordId: Long) {
        viewModelScope.launch {
            if (repository.requery(recordId)) {
                showNotice("Consulta reiniciada.")
            } else {
                showNotice("Registro não encontrado.")
            }
        }
    }

    fun downloadPhoto(recordId: Long) {
        viewModelScope.launch {
            val record = repository.observeById(recordId).first()
            if (record == null) {
                showNotice("Registro não encontrado.")
                return@launch
            }
            val file = container.photoStorage.resolve(record.photoPath)
            if (file == null) {
                showNotice("Não há fotografia disponível para baixar.")
                return@launch
            }
            when (val result = photoExporter.saveToGallery(file, record.displayPlate)) {
                is PhotoDownloadResult.Success ->
                    showNotice("Foto salva em ${result.relativePath}.")
                is PhotoDownloadResult.Failure ->
                    showNotice(result.message)
            }
        }
    }

    fun shareRecord(recordId: Long) {
        viewModelScope.launch {
            val record = repository.observeById(recordId).first()
            if (record == null) {
                showNotice("Registro não encontrado.")
                return@launch
            }
            val photo = container.photoStorage.resolve(record.photoPath)
            val plateCrop = container.photoStorage.resolve(record.plateCropPath)
            val uri = withContext(Dispatchers.IO) {
                shareCardGenerator.generate(record, photo, plateCrop)
            }
            if (uri == null) {
                showNotice("Não foi possível gerar a imagem para compartilhar.")
                return@launch
            }
            _sharePayload.value = SharePayload(
                uri = uri,
                subject = "VigiAuto - ${record.displayPlate}"
            )
        }
    }

    fun consumeSharePayload() {
        _sharePayload.value = null
    }

    fun removePhoto(recordId: Long) {
        viewModelScope.launch {
            repository.removePhoto(recordId)
            showNotice("Fotografia removida.")
        }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            repository.deleteRecord(recordId)
        }
    }

    fun attachPhotoFromGallery(recordId: Long, uri: Uri) {
        viewModelScope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val input = resolver.openInputStream(uri)
                        ?: throw IllegalStateException("Não foi possível abrir a imagem.")
                    val original = input.use { BitmapFactory.decodeStream(it) }
                        ?: throw IllegalStateException("Imagem inválida.")
                    val maxSide = 1600
                    val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height))
                    val scaled = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            original,
                            (original.width * scale).toInt().coerceAtLeast(1),
                            (original.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    } else {
                        original
                    }
                    val output = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
                    if (scaled !== original) scaled.recycle()
                    original.recycle()
                    output.toByteArray()
                }
            }.getOrNull()

            if (bytes == null) {
                showNotice("Não foi possível processar a imagem escolhida.")
                return@launch
            }

            val ok = repository.attachPhoto(recordId, bytes)
            showNotice(if (ok) "Fotografia atualizada." else "Falha ao salvar a fotografia.")
        }
    }

    fun showNotice(message: String) {
        viewModelScope.launch {
            _notice.value = message
            delay(3_500)
            if (_notice.value == message) _notice.value = null
        }
    }
}
