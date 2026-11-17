package br.com.vigiauto.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import br.com.vigiauto.domain.plate.PlateBox
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prepara um recorte da placa para uma segunda passada de OCR.
 *
 * O ML Kit é um modelo pré-treinado e não pode ser ajustado em tempo de
 * execução. O que funciona na prática é dar a ele uma imagem melhor: recortar
 * somente a região da placa, ampliar e aplicar contraste. O chamador entrega o
 * bitmap já na orientação vertical, para que a caixa e o recorte usem o mesmo
 * sistema de coordenadas. O Q perde a perna e o C perde a abertura com
 * facilidade no quadro completo; no recorte ampliado esses detalhes aparecem
 * melhor.
 */
object PlateCropProcessor {

    private const val PADDING_RATIO = 0.14f
    private const val MIN_TARGET_HEIGHT = 128f
    private const val MIN_SCALE = 2f
    private const val MAX_SCALE = 5f
    private const val MAX_TARGET_WIDTH = 3200

    /**
     * Retorna duas versões do recorte: uma colorida e outra em tons de cinza
     * com contraste realçado. O chamador deve reciclar todos os bitmaps.
     *
     * [source] precisa estar na orientação vertical e [box] nas mesmas
     * coordenadas. A rotação do sensor é aplicada antes de recortar; fazer o
     * contrário deslocava a caixa e produzia miniaturas cortadas.
     */
    fun prepareVariants(source: Bitmap, box: PlateBox): List<Bitmap> {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return emptyList()
        if (box.width < 16f || box.height < 8f) return emptyList()

        val padX = max(4f, box.width * PADDING_RATIO)
        val padY = max(4f, box.height * PADDING_RATIO)
        val left = floor(box.left - padX).toInt().coerceIn(0, source.width - 1)
        val top = floor(box.top - padY).toInt().coerceIn(0, source.height - 1)
        val right = ceil(box.right + padX).toInt().coerceIn(left + 1, source.width)
        val bottom = ceil(box.bottom + padY).toInt().coerceIn(top + 1, source.height)
        val width = right - left
        val height = bottom - top
        if (width < 8 || height < 4) return emptyList()

        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val scaled = upscale(cropped)
        if (scaled !== cropped) cropped.recycle()

        val enhanced = runCatching { enhanceContrast(scaled) }.getOrNull()
        return if (enhanced != null && enhanced !== scaled) {
            listOf(scaled, enhanced)
        } else {
            listOf(scaled)
        }
    }

    private fun upscale(source: Bitmap): Bitmap {
        val byHeight = MIN_TARGET_HEIGHT / source.height.toFloat()
        val byWidth = MAX_TARGET_WIDTH / source.width.toFloat()
        val scale = min(byHeight, byWidth).coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale <= 1.01f) return source

        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Tons de cinza + contraste moderado. Não usa binarização porque um limiar
     * agressivo pode apagar justamente a perna do Q ou a barra do G.
     */
    private fun enhanceContrast(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val saturation = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturation.postConcat(contrastMatrix)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(saturation)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
