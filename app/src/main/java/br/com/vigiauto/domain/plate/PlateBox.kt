package br.com.vigiauto.domain.plate

import kotlin.math.max
import kotlin.math.min

/** Caixa de coordenadas independente do Android, para uso no domínio e nos testes. */
data class PlateBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = max(0f, width) * max(0f, height)

    fun union(other: PlateBox): PlateBox = PlateBox(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom)
    )

    fun intersection(other: PlateBox): PlateBox? {
        val left = max(left, other.left)
        val top = max(top, other.top)
        val right = min(right, other.right)
        val bottom = min(bottom, other.bottom)
        return if (right > left && bottom > top) PlateBox(left, top, right, bottom) else null
    }

    /** Interseção sobre união, usada para associar caixas entre quadros. */
    fun iou(other: PlateBox): Float {
        val intersection = intersection(other)?.area ?: 0f
        val union = area + other.area - intersection
        return if (union <= 0f) 0f else (intersection / union).coerceIn(0f, 1f)
    }

    companion object {
        val EMPTY = PlateBox(0f, 0f, 0f, 0f)
    }
}
