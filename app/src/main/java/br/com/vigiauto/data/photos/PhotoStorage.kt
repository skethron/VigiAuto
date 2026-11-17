package br.com.vigiauto.data.photos

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Armazena as fotografias no diretório privado do aplicativo.
 * O banco guarda apenas o nome do arquivo (referência), nunca o binário da imagem.
 */
class PhotoStorage(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).also { if (!it.exists()) it.mkdirs() }

    fun resolve(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val file = File(directory, fileName)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun absolutePath(fileName: String): String = File(directory, fileName).absolutePath

    suspend fun saveJpeg(bytes: ByteArray, prefix: String = "plate"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val safePrefix = prefix
                .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                .ifBlank { "plate" }
            val fileName = "${safePrefix}_${System.currentTimeMillis()}_" +
                "${UUID.randomUUID().toString().take(8)}.jpg"
            File(directory, fileName).writeBytes(bytes)
            fileName
        }.getOrNull()
    }

    suspend fun delete(fileName: String?): Boolean = withContext(Dispatchers.IO) {
        if (fileName.isNullOrBlank()) return@withContext false
        runCatching { File(directory, fileName).delete() }.getOrDefault(false)
    }

    suspend fun cleanupOrphans(referencedFiles: Set<String>): Int = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext 0
        // Arquivos recém-criados podem estar entre a gravação e o UPDATE do
        // banco. Não os remova nessa janela para não deixar um registro
        // apontando para uma foto já apagada.
        val safeCutoff = System.currentTimeMillis() - 5 * 60_000L
        files.count { file ->
            file.name !in referencedFiles &&
                file.lastModified() < safeCutoff &&
                runCatching { file.delete() }.getOrDefault(false)
        }
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    companion object {
        private const val DIRECTORY_NAME = "photos"
    }
}
