package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.max
import kotlin.math.min

/** Integer pixel rectangle. [right] and [bottom] are exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom

    fun union(other: PixelRect) = PixelRect(
        min(left, other.left),
        min(top, other.top),
        max(right, other.right),
        max(bottom, other.bottom),
    )

    /** Grows the rectangle by [dx]/[dy] on each side and clips the result to [bounds]. */
    fun expandedWithin(dx: Int, dy: Int, bounds: PixelRect) = PixelRect(
        max(bounds.left, left - dx),
        max(bounds.top, top - dy),
        min(bounds.right, right + dx),
        min(bounds.bottom, bottom + dy),
    )

    fun coerceIn(bounds: PixelRect) = PixelRect(
        left.coerceIn(bounds.left, bounds.right),
        top.coerceIn(bounds.top, bounds.bottom),
        right.coerceIn(bounds.left, bounds.right),
        bottom.coerceIn(bounds.top, bounds.bottom),
    )

    fun overlapShare(other: PixelRect): Float {
        val w = max(0, min(right, other.right) - max(left, other.left))
        val h = max(0, min(bottom, other.bottom) - max(top, other.top))
        val intersection = w.toLong() * h
        val smaller = min(area, other.area)
        return if (smaller <= 0L) 0f else intersection.toFloat() / smaller
    }
}
