package br.com.vigiauto.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Converte o quadro YUV para Bitmap e aplica a rotação do sensor.
 *
 * O `ImageProxy.toBitmap()` devolve os pixels na orientação do buffer; o
 * detector precisa da imagem já na orientação em que o usuário vê a cena.
 */
internal fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw

    return try {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(
            raw,
            0,
            0,
            raw.width,
            raw.height,
            matrix,
            true
        )
        if (rotated !== raw) raw.recycle()
        rotated
    } catch (error: Throwable) {
        raw.recycle()
        throw error
    }
}
