package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.abs
import kotlin.math.max

/**
 * Recursive whitespace-gutter panel detector (XY-cut over local edge-density profiles), with a
 * border-line fallback for panels that touch directly with no gutter at all.
 *
 * Unlike a fixed background-color/ink-blob approach, this never assumes what color a gutter
 * or a panel is. At each step it scans the current region's rows and columns for a pixel-to-
 * pixel *edge density* (no reference color needed — only compares each pixel to its immediate
 * neighbor), and treats a contiguous run of low-edge-density rows/columns as a gutter,
 * regardless of whether that run is white, solid black (a common manga flashback convention),
 * or any other shade. There is no cap on how wide a gutter run can be, so a panel "floating" in
 * a large solid-black void (another real flashback style) is handled the same way as an
 * ordinary thin gutter. Real panel content — line art, screentone, dialogue, SFX — almost never
 * reads as flat at the pixel level, so it naturally survives as content rather than gutter, even
 * when it doesn't touch a drawn border and isn't itself one connected ink shape.
 *
 * Real manga pages routinely have panels that touch directly with no whitespace at all, divided
 * only by a thin drawn line that content bleeds up against on both sides — no gutter run exists
 * there at any tolerance. For that case, when no gutter is found, this falls back to a second
 * signal: a *border line*, detected as a sharp, narrow peak in how many columns (or rows) show a
 * strong transition from the row (or column) just before them — a real drawn line crosses nearly
 * every column at the same position, even while the row's own left-right content keeps its
 * ordinary edge density high.
 *
 * Both signals are tuned and validated against real, hand-labeled manga pages, not just
 * synthetic fixtures — synthetic fixtures alone previously passed while the algorithm failed
 * badly on real content.
 */
object PanelBoundaryDetector {

    /** Minimum luminance delta between neighboring pixels to count as a local edge. */
    private const val EDGE_LUMINANCE_DELTA = 15

    /** A row/column is gutter-like when its edge density stays under this fraction. */
    private const val GUTTER_DENSITY_TOLERANCE = 0.035f

    /**
     * A brief interruption of up to this many rows/columns (JPEG ringing at a strong
     * transition, a speech-bubble outline crossing the gutter) doesn't end an otherwise
     * clearly gutter-like run, as long as low-density rows/columns resume shortly after.
     */
    private const val GAP_BRIDGE_MAX = 3

    /** A border-line peak must reach at least this much transition density to count. */
    private const val LINE_PEAK_MIN_VALUE = 0.55f

    /** A border-line peak must exceed its region's average transition density by this ratio. */
    private const val LINE_PEAK_MIN_RATIO = 1.35f

    /** How strong a region's own edge must read, within the search window, to count as bordered. */
    private const val EDGE_BORDER_MIN_VALUE = 0.5f

    /** Search window (px, either side) when checking whether a region's own edge sits on a border. */
    private const val EDGE_SEARCH_TOLERANCE = 6

    /** Regions narrower or shorter than this (in downsampled detection pixels) stop recursing. */
    private const val MIN_REGION_SIZE_PX = 12

    /** Safety cap on recursion depth; far more than any real page needs. */
    private const val MAX_DEPTH = 8

    fun detect(buffer: PixelBuffer): List<PanelRect> {
        if (buffer.width < 3 || buffer.height < 3) return emptyList()

        val content = cropToContentBounds(buffer, Region(0, 0, buffer.width, buffer.height))
        if (content.width < 1 || content.height < 1) return emptyList()

        val leaves = mutableListOf<Region>()
        cut(buffer, content, depth = 0, leaves)

        return leaves.map { region ->
            PanelRect(
                left = region.left / buffer.width.toFloat(),
                top = region.top / buffer.height.toFloat(),
                right = region.right / buffer.width.toFloat(),
                bottom = region.bottom / buffer.height.toFloat(),
            )
        }
    }

    /** Left/top inclusive, right/bottom exclusive — the pixel range is `[left, right) x [top, bottom)`. */
    private data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private data class Run(val start: Int, val end: Int) {
        val length: Int get() = end - start + 1
    }

    private enum class Axis { HORIZONTAL, VERTICAL }

    /**
     * Trims purely blank outer page margins ONCE before recursion starts. Outer margins are
     * typically the widest blank areas on a page, so without this, they win the "widest gutter"
     * competition against real, narrower inter-panel gutters at every recursion level — peeling
     * off one thin margin sliver at a time and never reaching the real panel boundaries.
     *
     * Known limitation: this only trims the whole-page margin once, not a *local* margin
     * specific to one row/column of panels (e.g. a row whose panels don't reach as far right as
     * the row below). Re-cropping at every recursion level fixes that case but was found, when
     * validated against real hand-labeled manga pages, to cause far more spurious micro-splits
     * elsewhere than it fixes — a worse net trade. Left as a known gap pending a better fix.
     */
    private fun cropToContentBounds(buffer: PixelBuffer, region: Region): Region {
        var left = region.left
        var top = region.top
        var right = region.right
        var bottom = region.bottom

        while (top < bottom && rowEdgeDensitySingle(buffer, top, left, right) < GUTTER_DENSITY_TOLERANCE) top++
        while (bottom > top && rowEdgeDensitySingle(buffer, bottom - 1, left, right) < GUTTER_DENSITY_TOLERANCE) bottom--
        while (left < right && colEdgeDensitySingle(buffer, left, top, bottom) < GUTTER_DENSITY_TOLERANCE) left++
        while (right > left && colEdgeDensitySingle(buffer, right - 1, top, bottom) < GUTTER_DENSITY_TOLERANCE) right--

        return Region(left, top, right, bottom)
    }

    private fun cut(buffer: PixelBuffer, region: Region, depth: Int, leaves: MutableList<Region>) {
        if (region.width < 1 || region.height < 1) return

        // Skipped at depth 0: the top-level (post-margin-crop) region usually spans multiple
        // panels, and its own crop boundary can coincidentally read as "bordered" from the
        // content's own edges sitting close to it — checking there risks treating a whole
        // multi-panel page as one giant panel. A single bordered panel filling nearly the
        // whole page is a real but rarer case than a multi-panel page; it's handled by the
        // gutter/line search finding nothing to split, not by this check.
        if (depth >= 1 && edgeIsBordered(buffer, region)) {
            leaves += region
            return
        }

        if (depth >= MAX_DEPTH || region.width < MIN_REGION_SIZE_PX || region.height < MIN_REGION_SIZE_PX) {
            addLeafIfContent(buffer, region, leaves)
            return
        }

        val rowDensity = rowEdgeDensity(buffer, region)
        val colDensity = colEdgeDensity(buffer, region)

        val hGutter = widestLowDensityRun(rowDensity, GUTTER_DENSITY_TOLERANCE)
        val vGutter = widestLowDensityRun(colDensity, GUTTER_DENSITY_TOLERANCE)

        if (hGutter != null || vGutter != null) {
            val axis = chooseSplitAxis(hGutter, vGutter)
            when (axis) {
                Axis.HORIZONTAL -> {
                    val cutRow = region.top + hGutter!!.start + (hGutter.end - hGutter.start) / 2
                    cut(buffer, region.copy(bottom = cutRow), depth + 1, leaves)
                    cut(buffer, region.copy(top = cutRow), depth + 1, leaves)
                }
                Axis.VERTICAL -> {
                    val cutCol = region.left + vGutter!!.start + (vGutter.end - vGutter.start) / 2
                    cut(buffer, region.copy(right = cutCol), depth + 1, leaves)
                    cut(buffer, region.copy(left = cutCol), depth + 1, leaves)
                }
            }
            return
        }

        // No whitespace gutter anywhere in this region — fall back to looking for a drawn
        // border LINE instead (panels touching directly, separated only by a thin ink line
        // that content bleeds up against on both sides, so it never reads as low-density).
        val rowTransition = rowVerticalTransitionDensity(buffer, region)
        val colTransition = colHorizontalTransitionDensity(buffer, region)
        val hPeak = findLinePeak(rowTransition)
        val vPeak = findLinePeak(colTransition)

        if (hPeak == null && vPeak == null) {
            addLeafIfContent(buffer, region, leaves)
            return
        }

        if (vPeak == null || (hPeak != null && rowTransition[hPeak] >= colTransition[vPeak])) {
            val cutRow = region.top + hPeak!!
            cut(buffer, region.copy(bottom = cutRow), depth + 1, leaves)
            cut(buffer, region.copy(top = cutRow), depth + 1, leaves)
        } else {
            val cutCol = region.left + vPeak
            cut(buffer, region.copy(right = cutCol), depth + 1, leaves)
            cut(buffer, region.copy(left = cutCol), depth + 1, leaves)
        }
    }

    private fun chooseSplitAxis(horizontal: Run?, vertical: Run?): Axis {
        return if (vertical == null || (horizontal != null && horizontal.length >= vertical.length)) {
            Axis.HORIZONTAL
        } else {
            Axis.VERTICAL
        }
    }

    /**
     * A leaf that reached here (no further valid split, or a safety floor) might itself be a
     * pure gutter/background sliver rather than real panel content — e.g. a wide blank margin
     * that kept bisecting until it hit [MIN_REGION_SIZE_PX]. Only keep it as a panel if it has
     * meaningfully more than noise-level edge content somewhere inside it.
     */
    private fun addLeafIfContent(buffer: PixelBuffer, region: Region, leaves: MutableList<Region>) {
        if (region.width < 1 || region.height < 1) return
        if (regionEdgeDensity(buffer, region) >= GUTTER_DENSITY_TOLERANCE) {
            leaves += region
        }
    }

    /**
     * Is this region already enclosed by a drawn frame on all four of its OWN edges? If so,
     * it's almost certainly already exactly one manga panel (panels are conventionally
     * bordered individually, not grouped multiple-to-a-frame) — real internal complexity
     * (multiple speech bubbles, big SFX text) belongs together inside it and must not be split
     * further, no matter what gutter/line signal a stray gap inside it produces.
     *
     * A region's own bounds come from a mix of the initial content-crop and prior split
     * decisions, neither of which guarantees landing exactly ON a border row/column — a
     * gutter-based cut lands mid-gutter, not on the panel's drawn edge. So each edge is checked
     * across a small search window rather than requiring the literal boundary pixel to be the
     * border, picking the strongest match nearby. An edge already at the page's own boundary
     * (no "outside" to compare against) doesn't count against framing — it just can't be
     * checked, so it isn't required.
     */
    private fun edgeIsBordered(buffer: PixelBuffer, region: Region): Boolean {
        var checks = 0
        var passed = 0

        if (region.top > 0) {
            checks++
            val v = bestRowTransition(
                buffer,
                (region.top - EDGE_SEARCH_TOLERANCE)..(region.top + EDGE_SEARCH_TOLERANCE),
                region.left,
                region.right,
            )
            if (v >= EDGE_BORDER_MIN_VALUE) passed++
        }
        if (region.bottom < buffer.height) {
            checks++
            val v = bestRowTransition(
                buffer,
                (region.bottom - EDGE_SEARCH_TOLERANCE)..(region.bottom + EDGE_SEARCH_TOLERANCE),
                region.left,
                region.right,
            )
            if (v >= EDGE_BORDER_MIN_VALUE) passed++
        }
        if (region.left > 0) {
            checks++
            val v = bestColTransition(
                buffer,
                (region.left - EDGE_SEARCH_TOLERANCE)..(region.left + EDGE_SEARCH_TOLERANCE),
                region.top,
                region.bottom,
            )
            if (v >= EDGE_BORDER_MIN_VALUE) passed++
        }
        if (region.right < buffer.width) {
            checks++
            val v = bestColTransition(
                buffer,
                (region.right - EDGE_SEARCH_TOLERANCE)..(region.right + EDGE_SEARCH_TOLERANCE),
                region.top,
                region.bottom,
            )
            if (v >= EDGE_BORDER_MIN_VALUE) passed++
        }

        // Only trust this as "framed" if there was at least one real edge to check, and every
        // checked edge passed. A region with zero checkable edges (fills the whole page) isn't
        // meaningfully "framed" by this test either way.
        return checks > 0 && passed == checks
    }

    private fun bestRowTransition(buffer: PixelBuffer, yCandidates: IntRange, left: Int, right: Int): Float {
        val span = right - left
        if (span <= 0) return 0f
        var best = 0f
        for (y in yCandidates) {
            if (y <= 0 || y >= buffer.height) continue
            var strong = 0
            for (x in left until right) {
                if (abs(buffer.luminanceAt(x, y) - buffer.luminanceAt(x, y - 1)) > EDGE_LUMINANCE_DELTA) strong++
            }
            best = max(best, strong.toFloat() / span)
        }
        return best
    }

    private fun bestColTransition(buffer: PixelBuffer, xCandidates: IntRange, top: Int, bottom: Int): Float {
        val span = bottom - top
        if (span <= 0) return 0f
        var best = 0f
        for (x in xCandidates) {
            if (x <= 0 || x >= buffer.width) continue
            var strong = 0
            for (y in top until bottom) {
                if (abs(buffer.luminanceAt(x, y) - buffer.luminanceAt(x - 1, y)) > EDGE_LUMINANCE_DELTA) strong++
            }
            best = max(best, strong.toFloat() / span)
        }
        return best
    }

    private fun rowEdgeDensitySingle(buffer: PixelBuffer, y: Int, left: Int, right: Int): Float {
        val samples = right - left - 1
        if (samples <= 0) return 0f
        var edges = 0
        var prev = buffer.luminanceAt(left, y)
        for (x in left + 1 until right) {
            val curr = buffer.luminanceAt(x, y)
            if (abs(curr - prev) > EDGE_LUMINANCE_DELTA) edges++
            prev = curr
        }
        return edges.toFloat() / samples
    }

    private fun colEdgeDensitySingle(buffer: PixelBuffer, x: Int, top: Int, bottom: Int): Float {
        val samples = bottom - top - 1
        if (samples <= 0) return 0f
        var edges = 0
        var prev = buffer.luminanceAt(x, top)
        for (y in top + 1 until bottom) {
            val curr = buffer.luminanceAt(x, y)
            if (abs(curr - prev) > EDGE_LUMINANCE_DELTA) edges++
            prev = curr
        }
        return edges.toFloat() / samples
    }

    private fun rowEdgeDensity(buffer: PixelBuffer, region: Region): FloatArray {
        val density = FloatArray(region.height)
        if (region.width < 2) return density
        for (y in region.top until region.bottom) {
            density[y - region.top] = rowEdgeDensitySingle(buffer, y, region.left, region.right)
        }
        return density
    }

    private fun colEdgeDensity(buffer: PixelBuffer, region: Region): FloatArray {
        val density = FloatArray(region.width)
        if (region.height < 2) return density
        for (x in region.left until region.right) {
            density[x - region.left] = colEdgeDensitySingle(buffer, x, region.top, region.bottom)
        }
        return density
    }

    /** Overall content density for a whole region, reusing the row-direction edge scan. */
    private fun regionEdgeDensity(buffer: PixelBuffer, region: Region): Float {
        if (region.width < 2) return 0f
        val density = rowEdgeDensity(buffer, region)
        return density.average().toFloat()
    }

    /**
     * For each row y (except the region's first), the fraction of columns whose luminance jumps
     * sharply from the row directly above — i.e. does a horizontal LINE cross at this exact y,
     * even if content on both sides keeps this row's own left-right density high.
     */
    private fun rowVerticalTransitionDensity(buffer: PixelBuffer, region: Region): FloatArray {
        val density = FloatArray(region.height)
        if (region.width < 1) return density
        for (y in region.top + 1 until region.bottom) {
            var strong = 0
            for (x in region.left until region.right) {
                if (abs(buffer.luminanceAt(x, y) - buffer.luminanceAt(x, y - 1)) > EDGE_LUMINANCE_DELTA) strong++
            }
            density[y - region.top] = strong.toFloat() / region.width
        }
        return density
    }

    private fun colHorizontalTransitionDensity(buffer: PixelBuffer, region: Region): FloatArray {
        val density = FloatArray(region.width)
        if (region.height < 1) return density
        for (x in region.left + 1 until region.right) {
            var strong = 0
            for (y in region.top until region.bottom) {
                if (abs(buffer.luminanceAt(x, y) - buffer.luminanceAt(x - 1, y)) > EDGE_LUMINANCE_DELTA) strong++
            }
            density[x - region.left] = strong.toFloat() / region.height
        }
        return density
    }

    /**
     * A real border line stands out as a sharp, narrow peak well above its local
     * neighborhood's baseline — not just the single highest value in the whole profile (busy
     * content can have high transition density too, just without a narrow, standout spike).
     * Requires the peak to clear both an absolute floor and a margin over the region's average.
     */
    private fun findLinePeak(density: FloatArray): Int? {
        if (density.isEmpty()) return null
        var peakIndex = 0
        for (i in density.indices) {
            if (density[i] > density[peakIndex]) peakIndex = i
        }
        val peakValue = density[peakIndex]
        val average = density.average().toFloat()
        if (peakValue < LINE_PEAK_MIN_VALUE) return null
        if (average <= 0f || peakValue / average < LINE_PEAK_MIN_RATIO) return null
        return peakIndex
    }

    private fun widestLowDensityRun(density: FloatArray, tolerance: Float): Run? {
        // A brief interruption (up to GAP_BRIDGE_MAX rows/columns at or above tolerance)
        // doesn't end the run, as long as low-density rows/columns resume shortly after.
        // `end` only ever advances to a CONFIRMED low-density index — a bridged gap is never
        // counted as part of the run unless more low-density indices follow it.
        var bestStart = -1
        var bestEnd = -1
        var bestLength = 0
        var i = 0

        while (i < density.size) {
            if (density[i] >= tolerance) {
                i++
                continue
            }
            var start = i
            var end = i
            var j = i + 1
            while (j < density.size) {
                if (density[j] < tolerance) {
                    end = j
                    j++
                } else {
                    val gapStart = j
                    while (j < density.size && density[j] >= tolerance) j++
                    val gapLength = j - gapStart
                    if (gapLength <= GAP_BRIDGE_MAX && j < density.size) {
                        continue
                    }
                    break
                }
            }
            val length = end - start + 1
            if (length > bestLength) {
                bestLength = length
                bestStart = start
                bestEnd = end
            }
            i = end + 1
        }

        return if (bestStart == -1) null else Run(bestStart, bestEnd)
    }
}
