package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.max
import kotlin.math.roundToInt

enum class BoxKind { BUBBLE, CAPTION, SFX }

/**
 * One piece of text found by the model, with its translation.
 * Coordinates are on Gemini's 0-1000 grid: top, left, bottom, right.
 */
data class TextBox(
    val yMin: Int,
    val xMin: Int,
    val yMax: Int,
    val xMax: Int,
    val kind: BoxKind,
    val text: String,
    val sourceText: String = "",
) {
    /** Converts the normalized box to pixels of an image with the given size. */
    fun toPixelRect(imageWidth: Int, imageHeight: Int): PixelRect {
        val left = (xMin / GRID * imageWidth).roundToInt().coerceIn(0, max(0, imageWidth - 1))
        val top = (yMin / GRID * imageHeight).roundToInt().coerceIn(0, max(0, imageHeight - 1))
        val right = (xMax / GRID * imageWidth).roundToInt().coerceIn(left + 1, imageWidth)
        val bottom = (yMax / GRID * imageHeight).roundToInt().coerceIn(top + 1, imageHeight)
        return PixelRect(left, top, right, bottom)
    }

    companion object {
        const val GRID = 1000f
    }
}
