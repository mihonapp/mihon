package tachiyomi.translation.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A rectangle stored in the page's own image coordinate space, normalized to 0..1.
 *
 * Storing normalized values (rather than view or bitmap pixels) keeps a drawn box valid across
 * zoom levels, screen rotations and re-decodes at a different sample size.
 */
@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height

    val isValid: Boolean get() = width > 0f && height > 0f

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    fun intersects(other: NormalizedRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** Overlap area, used to decide whether a drawn box has picked up a detected region. */
    fun intersectionArea(other: NormalizedRect): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        return if (w <= 0f || h <= 0f) 0f else w * h
    }

    fun union(other: NormalizedRect): NormalizedRect = NormalizedRect(
        min(left, other.left),
        min(top, other.top),
        max(right, other.right),
        max(bottom, other.bottom),
    )

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** Clamps into 0..1 and fixes inverted coordinates. */
    fun clampToUnit(): NormalizedRect {
        val l = min(left, right).coerceIn(0f, 1f)
        val r = max(left, right).coerceIn(0f, 1f)
        val t = min(top, bottom).coerceIn(0f, 1f)
        val b = max(top, bottom).coerceIn(0f, 1f)
        return NormalizedRect(l, t, r, b)
    }

    fun toPixels(imageWidth: Int, imageHeight: Int): PixelRect = PixelRect(
        left = (left * imageWidth).roundToInt().coerceIn(0, imageWidth),
        top = (top * imageHeight).roundToInt().coerceIn(0, imageHeight),
        right = (right * imageWidth).roundToInt().coerceIn(0, imageWidth),
        bottom = (bottom * imageHeight).roundToInt().coerceIn(0, imageHeight),
    )

    companion object {
        val EMPTY = NormalizedRect(0f, 0f, 0f, 0f)

        fun fromPixels(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            imageWidth: Int,
            imageHeight: Int,
        ): NormalizedRect {
            if (imageWidth <= 0 || imageHeight <= 0) return EMPTY
            return NormalizedRect(
                left = left.toFloat() / imageWidth,
                top = top.toFloat() / imageHeight,
                right = right.toFloat() / imageWidth,
                bottom = bottom.toFloat() / imageHeight,
            ).clampToUnit()
        }
    }
}

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val shortSide: Int get() = min(width, height)

    fun clampToBounds(imageWidth: Int, imageHeight: Int): PixelRect = PixelRect(
        left = left.coerceIn(0, imageWidth),
        top = top.coerceIn(0, imageHeight),
        right = right.coerceIn(0, imageWidth),
        bottom = bottom.coerceIn(0, imageHeight),
    )
}
