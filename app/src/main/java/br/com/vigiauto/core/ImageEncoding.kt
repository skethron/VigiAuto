package br.com.vigiauto.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

object ImageEncoding {

    /**
     * Codifica NV21 em JPEG e aplica a rotação necessária para a imagem ficar
     * na orientação correta. Executado apenas quando uma placa é confirmada.
     */
    fun encodeJpeg(
        nv21: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        quality: Int = 88
    ): ByteArray? {
        if (width <= 0 || height <= 0 || nv21.isEmpty()) return null
        return runCatching {
            val output = ByteArrayOutputStream()
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, output)) return null
            val original = output.toByteArray()
            if (rotationDegrees % 360 == 0) {
                original
            } else {
                rotateJpeg(original, rotationDegrees, quality)
            }
        }.getOrNull()
    }

    /**
     * Decodifica o NV21 já na orientação em que a cena é exibida. Usado apenas
     * quando o pipeline de contingência precisa recortar a placa a partir do
     * quadro guardado no ring buffer.
     */
    fun decodeUprightBitmap(
        nv21: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        quality: Int = 92
    ): Bitmap? {
        val jpeg = encodeJpeg(nv21, width, height, rotationDegrees, quality) ?: return null
        return runCatching {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        }.getOrNull()
    }

    private fun rotateJpeg(jpeg: ByteArray, rotationDegrees: Int, quality: Int): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val output = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
        return output.toByteArray()
    }
}
