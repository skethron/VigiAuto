package br.com.vigiauto.data.photos

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface PhotoDownloadResult {
    data class Success(val relativePath: String) : PhotoDownloadResult
    data class Failure(val message: String) : PhotoDownloadResult
}

/**
 * Copia a fotografia privada para a galeria pública sem reencodar o JPEG.
 * No Android 10+ usa MediaStore/RELATIVE_PATH; no Android 8/9 usa a pasta
 * Pictures com a permissão de armazenamento antiga.
 */
class PhotoExporter(private val context: Context) {

    suspend fun saveToGallery(source: File, plate: String): PhotoDownloadResult =
        withContext(Dispatchers.IO) {
            if (!source.exists() || !source.isFile) {
                return@withContext PhotoDownloadResult.Failure("A fotografia não está mais disponível.")
            }

            val fileName = buildFileName(plate)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(source, fileName)
            } else {
                saveLegacy(source, fileName)
            }
        }

    private fun saveWithMediaStore(source: File, fileName: String): PhotoDownloadResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/VigiAuto"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
            ?: return PhotoDownloadResult.Failure("Não foi possível criar a imagem na galeria.")

        return try {
            val output = resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Saída da galeria indisponível.")
            output.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            PhotoDownloadResult.Success("Pictures/VigiAuto/$fileName")
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            PhotoDownloadResult.Failure(
                "Falha ao salvar na galeria: ${error.message ?: "erro desconhecido"}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(source: File, fileName: String): PhotoDownloadResult {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return PhotoDownloadResult.Failure(
                "Permita o acesso ao armazenamento para baixar a fotografia."
            )
        }

        return runCatching {
            val pictures = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val directory = File(pictures, "VigiAuto")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Não foi possível criar a pasta VigiAuto.")
            }
            val destination = uniqueFile(directory, fileName)
            source.copyTo(destination, overwrite = false)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destination.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            PhotoDownloadResult.Success("Pictures/VigiAuto/${destination.name}")
        }.getOrElse { error ->
            PhotoDownloadResult.Failure(
                "Falha ao salvar a fotografia: ${error.message ?: "erro desconhecido"}"
            )
        }
    }

    private fun buildFileName(plate: String): String {
        val safePlate = plate.filter { it.isLetterOrDigit() }.ifBlank { "veiculo" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "VigiAuto_${safePlate}_$stamp.jpg"
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val base = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "jpg")
        var candidate = File(directory, fileName)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${base}_$index.$extension")
            index++
        }
        return candidate
    }
}
