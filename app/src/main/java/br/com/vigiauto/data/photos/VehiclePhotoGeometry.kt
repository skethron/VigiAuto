package br.com.vigiauto.data.photos

import br.com.vigiauto.domain.plate.PlateBox
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Retângulo em pixels de um quadro já na orientação vertical. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Geometria pura da fotografia do veículo.
 *
 * A placa fica na parte baixa do veículo; por isso o quadrado é deslocado um
 * pouco para cima em relação ao centro da caixa. O deslocamento preserva o
 * veículo e mantém a placa visível na parte inferior da foto.
 */
object VehiclePhotoGeometry {

    private const val MIN_SQUARE_FRACTION = 0.45f
    private const val PLATE_WIDTH_FACTOR = 4.4f
    private const val PLATE_HEIGHT_FACTOR = 7.5f
    private const val VERTICAL_LIFT_FRACTION = 0.16f

    /** Margem da miniatura: evita cortar a borda da placa e os parafusos. */
    private const val THUMB_HORIZONTAL_MARGIN = 0.10f
    private const val THUMB_VERTICAL_MARGIN = 0.14f
    private const val THUMB_MIN_MARGIN_PX = 2f

    fun squareAround(box: PlateBox, frameWidth: Int, frameHeight: Int): PixelRect? {
        if (frameWidth <= 0 || frameHeight <= 0) return null
        val frameMin = min(frameWidth, frameHeight).coerceAtLeast(1)
        val plateWidth = box.width.takeIf { it > 0f } ?: (frameMin * 0.20f)
        val plateHeight = box.height.takeIf { it > 0f } ?: (frameMin * 0.08f)

        val desired = max(plateWidth * PLATE_WIDTH_FACTOR, plateHeight * PLATE_HEIGHT_FACTOR)
        val side = desired
            .coerceAtLeast(frameMin * MIN_SQUARE_FRACTION)
            .coerceAtMost(frameMin.toFloat())
            .roundToInt()
            .coerceAtLeast(1)

        val centerX = if (box.width > 0f) (box.left + box.right) / 2f else frameWidth / 2f
        val centerY = if (box.height > 0f) {
            (box.top + box.bottom) / 2f - side * VERTICAL_LIFT_FRACTION
        } else {
            frameHeight / 2f
        }

        val left = (centerX - side / 2f).roundToInt()
            .coerceIn(0, (frameWidth - side).coerceAtLeast(0))
        val top = (centerY - side / 2f).roundToInt()
            .coerceIn(0, (frameHeight - side).coerceAtLeast(0))
        return PixelRect(left, top, left + side, top + side)
    }

    fun plateRect(box: PlateBox, frameWidth: Int, frameHeight: Int): PixelRect? {
        if (frameWidth <= 0 || frameHeight <= 0) return null
        if (box.width < 4f || box.height < 2f) return null

        val left = floor(box.left).toInt().coerceIn(0, frameWidth - 1)
        val top = floor(box.top).toInt().coerceIn(0, frameHeight - 1)
        val right = ceil(box.right).toInt().coerceIn(left + 1, frameWidth)
        val bottom = ceil(box.bottom).toInt().coerceIn(top + 1, frameHeight)
        if (right - left < 4 || bottom - top < 2) return null
        return PixelRect(left, top, right, bottom)
    }

    /**
     * Retângulo da miniatura nas coordenadas da **fotografia** do veículo.
     *
     * A fotografia é o recorte quadrado [square] do quadro, eventualmente
     * reduzido para no máximo 1080 px. A caixa da placa é mapeada para esse
     * mesmo quadrado e recebe uma margem curta. Assim a miniatura é literalmente
     * um pedaço da foto exibida, e não uma região de outro enquadramento.
     */
    fun plateThumbRect(
        box: PlateBox,
        square: PixelRect,
        photoWidth: Int,
        photoHeight: Int
    ): PixelRect? {
        if (square.width <= 0 || square.height <= 0) return null
        if (photoWidth <= 0 || photoHeight <= 0) return null
        if (box.width < 4f || box.height < 2f) return null

        val centerX = (box.left + box.right) / 2f
        val centerY = (box.top + box.bottom) / 2f
        if (centerX !in square.left.toFloat()..square.right.toFloat() ||
            centerY !in square.top.toFloat()..square.bottom.toFloat()
        ) {
            // Caixa em coordenadas de outro recorte: melhor não exibir uma
            // miniatura errada do que recortar uma região sem relação com a placa.
            return null
        }

        val padX = max(THUMB_MIN_MARGIN_PX, box.width * THUMB_HORIZONTAL_MARGIN)
        val padY = max(THUMB_MIN_MARGIN_PX, box.height * THUMB_VERTICAL_MARGIN)
        val sourceLeft = floor(box.left - padX).toInt().coerceIn(square.left, square.right - 1)
        val sourceTop = floor(box.top - padY).toInt().coerceIn(square.top, square.bottom - 1)
        val sourceRight = ceil(box.right + padX).toInt().coerceIn(sourceLeft + 1, square.right)
        val sourceBottom = ceil(box.bottom + padY).toInt().coerceIn(sourceTop + 1, square.bottom)

        val scaleX = photoWidth.toFloat() / square.width
        val scaleY = photoHeight.toFloat() / square.height
        val left = floor((sourceLeft - square.left) * scaleX).toInt().coerceIn(0, photoWidth - 1)
        val top = floor((sourceTop - square.top) * scaleY).toInt().coerceIn(0, photoHeight - 1)
        val right = ceil((sourceRight - square.left) * scaleX).toInt()
            .coerceIn(left + 1, photoWidth)
        val bottom = ceil((sourceBottom - square.top) * scaleY).toInt()
            .coerceIn(top + 1, photoHeight)
        if (right - left < 4 || bottom - top < 2) return null
        return PixelRect(left, top, right, bottom)
    }

    fun normalize(value: Float, total: Int): Float? {
        if (total <= 0 || !value.isFinite()) return null
        return (value / total).coerceIn(0f, 1f)
    }
}
