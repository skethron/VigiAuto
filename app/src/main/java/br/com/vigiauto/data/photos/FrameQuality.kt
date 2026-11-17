package br.com.vigiauto.data.photos

import kotlin.math.abs

/**
 * Mede nitidez no plano de luminância (Y) do NV21.
 *
 * Usa a resposta de um Laplaciano simples em pixels amostrados. É barato o
 * suficiente para rodar junto da conversão de cada quadro e serve para escolher
 * a fotografia menos tremida quando o veículo está em movimento.
 */
object FrameQuality {

    private const val SAMPLE_STEP = 4

    fun sharpness(nv21: ByteArray, width: Int, height: Int): Float {
        val lumaSize = width * height
        if (width < 3 || height < 3 || nv21.size < lumaSize) return 0f

        var sum = 0.0
        var samples = 0
        var y = SAMPLE_STEP
        while (y < height - SAMPLE_STEP) {
            var x = SAMPLE_STEP
            while (x < width - SAMPLE_STEP) {
                val center = nv21[y * width + x].toInt() and 0xFF
                val left = nv21[y * width + x - SAMPLE_STEP].toInt() and 0xFF
                val right = nv21[y * width + x + SAMPLE_STEP].toInt() and 0xFF
                val up = nv21[(y - SAMPLE_STEP) * width + x].toInt() and 0xFF
                val down = nv21[(y + SAMPLE_STEP) * width + x].toInt() and 0xFF
                sum += abs(4 * center - left - right - up - down)
                samples++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return if (samples == 0) 0f else (sum / samples).toFloat()
    }
}
