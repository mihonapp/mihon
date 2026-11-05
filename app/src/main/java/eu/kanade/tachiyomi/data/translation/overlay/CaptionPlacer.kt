package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.max
import kotlin.math.min

/**
 * Chooses where the caption box of a translation goes when the original text stays on the page. It sits right below
 * the original text, moves further down when it would cover an earlier caption, and goes above the text when the
 * page ends below it. Pure geometry, so it can be tested without Android.
 */
object CaptionPlacer {

    private const val GAP_RATIO = 0.008f
    private const val MIN_WIDTH_RATIO = 0.25f
    private const val MAX_WIDTH_RATIO = 0.92f
    private const val WIDTH_GROWTH = 1.15f
    private const val HEIGHT_TO_WIDTH = 0.3f
    private const val MAX_SHIFTS = 8

    /**
     * @param box where the original text is
     * @param page the whole page
     * @param placed captions already placed on this page
     */
    fun place(box: PixelRect, page: PixelRect, placed: List<PixelRect>): PixelRect {
        val gap = max(2, (page.width * GAP_RATIO).toInt())
        val maxWidth = (page.width * MAX_WIDTH_RATIO).toInt().coerceAtLeast(1)
        val minWidth = min((page.width * MIN_WIDTH_RATIO).toInt(), maxWidth)
        val width = (box.width * WIDTH_GROWTH).toInt().coerceIn(minWidth, maxWidth)
        val height = max(box.height, (width * HEIGHT_TO_WIDTH).toInt()).coerceIn(1, page.height)
        val left = (box.centerX - width / 2).coerceIn(page.left, max(page.left, page.right - width))

        fun rectAt(top: Int) = PixelRect(left, top, left + width, top + height)

        var top = box.bottom + gap
        for (i in 0 until MAX_SHIFTS) {
            val hit = placed.firstOrNull { overlaps(it, rectAt(top)) } ?: break
            top = hit.bottom + gap
        }

        if (top + height > page.bottom) {
            var above = box.top - gap - height
            for (i in 0 until MAX_SHIFTS) {
                val hit = placed.firstOrNull { overlaps(it, rectAt(above)) } ?: break
                above = hit.top - gap - height
            }
            top = above
        }

        return rectAt(top.coerceIn(page.top, max(page.top, page.bottom - height)))
    }

    private fun overlaps(a: PixelRect, b: PixelRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
