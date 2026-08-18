package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.max
import kotlin.math.min

/**
 * Fills coverage gaps. If the detected panels leave a large rectangular region of the page
 * uncovered, that region was most likely a panel the model missed — so we add it as a panel.
 *
 * The page is sampled on a grid and we repeatedly carve out the *largest empty rectangle* of
 * uncovered cells: each one big enough (and not a thin sliver) becomes an extra panel, then is
 * marked covered so the next-largest hole can be found. Using maximal rectangles — rather than
 * connected components — is what makes this work on real pages: the thin margin/gutter frame that
 * surrounds and connects everything never forms a large rectangle, so it's ignored, while a genuine
 * missed panel (bounded by its neighbours) is isolated cleanly. Ordering/numbering happens
 * afterwards in the pipeline. Works in normalized `[0,1]` coordinates.
 */
object PanelGapFiller {

    data class Config(
        /** An empty rectangle must cover at least this fraction of the page to become a panel. */
        val minAreaFraction: Float = 0.07f,
        /** ...and each side must be at least this fraction of the page (rejects thin margin strips). */
        val minSideFraction: Float = 0.12f,
        /**
         * ...and it must not be a sliver: real panels aren't this elongated, but a margin/gutter
         * that runs the full length of one axis (e.g. the page's right-edge trim margin) can still
         * clear [minSideFraction] on its short side while being unmistakably not panel-shaped.
         */
        val maxAspectRatio: Float = 4f,
        /** Sampling resolution (cells per axis). */
        val grid: Int = 64,
        /** Safety cap on how many missed panels to recover from one page. */
        val maxFills: Int = 8,
    )

    fun fill(panels: List<PanelRect>, config: Config = Config()): List<PanelRect> {
        if (panels.isEmpty()) return panels
        val n = config.grid

        // Mark a cell covered if its centre falls inside any panel.
        val covered = BooleanArray(n * n)
        for (gy in 0 until n) {
            val cy = (gy + 0.5f) / n
            for (gx in 0 until n) {
                val cx = (gx + 0.5f) / n
                covered[gy * n + gx] = panels.any {
                    cx >= it.left && cx <= it.right && cy >= it.top && cy <= it.bottom
                }
            }
        }

        val cellArea = 1f / (n * n)
        val gaps = ArrayList<PanelRect>()
        for (pass in 0 until config.maxFills) {
            val rect = largestEmptyRectangle(covered, n) ?: break
            // The largest empty rectangle only shrinks each pass, so once it's below the area
            // threshold nothing larger remains — stop.
            if (rect.cells * cellArea < config.minAreaFraction) break
            // Always claim the rectangle so the next pass finds a different hole; only emit it as a
            // panel if it isn't a thin sliver (a margin/gutter strip rather than a real panel).
            val wFrac = (rect.x1 - rect.x0 + 1).toFloat() / n
            val hFrac = (rect.y1 - rect.y0 + 1).toFloat() / n
            for (yy in rect.y0..rect.y1) for (xx in rect.x0..rect.x1) covered[yy * n + xx] = true
            val aspectRatio = max(wFrac, hFrac) / min(wFrac, hFrac)
            val isPanelShaped = wFrac >= config.minSideFraction &&
                hFrac >= config.minSideFraction &&
                aspectRatio <= config.maxAspectRatio
            if (isPanelShaped) {
                gaps += PanelRect(
                    left = rect.x0.toFloat() / n,
                    top = rect.y0.toFloat() / n,
                    right = (rect.x1 + 1).toFloat() / n,
                    bottom = (rect.y1 + 1).toFloat() / n,
                )
            }
        }

        return if (gaps.isEmpty()) panels else panels + gaps
    }

    private data class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val cells: Int)

    /**
     * Largest axis-aligned rectangle of uncovered cells, via the classic histogram method (build a
     * column-height histogram per row, then find the largest rectangle in that histogram). Returns
     * null if nothing is uncovered.
     */
    private fun largestEmptyRectangle(covered: BooleanArray, n: Int): Rect? {
        val height = IntArray(n)
        var best: Rect? = null
        for (y in 0 until n) {
            for (x in 0 until n) {
                height[x] = if (covered[y * n + x]) 0 else height[x] + 1
            }
            // Largest rectangle in this histogram (row y is the bottom edge), tracking its bounds.
            val stack = ArrayDeque<Int>() // indices of increasing bar heights
            var x = 0
            while (x <= n) {
                val h = if (x < n) height[x] else 0
                while (stack.isNotEmpty() && height[stack.last()] >= h) {
                    val barHeight = height[stack.removeLast()]
                    val left = if (stack.isEmpty()) 0 else stack.last() + 1
                    val right = x - 1
                    if (barHeight > 0) {
                        val cells = barHeight * (right - left + 1)
                        if (best == null || cells > best!!.cells) {
                            best = Rect(left, y - barHeight + 1, right, y, cells)
                        }
                    }
                }
                if (x < n) stack.addLast(x)
                x++
            }
        }
        return best
    }
}
