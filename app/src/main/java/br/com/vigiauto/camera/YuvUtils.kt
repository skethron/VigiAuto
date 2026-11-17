package br.com.vigiauto.camera

import androidx.camera.core.ImageProxy

object YuvUtils {

    /**
     * Converte YUV_420_888 em NV21 respeitando rowStride/pixelStride dos planos.
     */
    fun toNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val nv21 = ByteArray(width * height + chromaWidth * chromaHeight * 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        var offset = 0
        for (row in 0 until height) {
            val sourcePosition = row * yPlane.rowStride
            if (sourcePosition + width <= yBuffer.limit()) {
                yBuffer.position(sourcePosition)
                yBuffer.get(nv21, offset, width)
            } else {
                for (column in 0 until width) {
                    val index = sourcePosition + column
                    nv21[offset + column] = if (index < yBuffer.limit()) yBuffer.get(index) else 0
                }
            }
            offset += width
        }

        val uvRowStride = vPlane.rowStride
        val uvPixelStride = vPlane.pixelStride
        for (row in 0 until chromaHeight) {
            for (column in 0 until chromaWidth) {
                val vIndex = row * uvRowStride + column * uvPixelStride
                val uIndex = row * uPlane.rowStride + column * uPlane.pixelStride
                nv21[offset++] = if (vIndex < vBuffer.limit()) vBuffer.get(vIndex) else 0
                nv21[offset++] = if (uIndex < uBuffer.limit()) uBuffer.get(uIndex) else 0
            }
        }

        return nv21
    }
}
