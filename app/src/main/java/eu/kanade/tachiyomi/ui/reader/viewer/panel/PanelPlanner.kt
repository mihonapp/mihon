package eu.kanade.tachiyomi.ui.reader.viewer.panel

/**
 * Post-processes the ML model's panels into a nicer set of zoom regions:
 *
 *  - **Merge** runs of panels that are both *consecutive in reading order* and *spatially adjacent*
 *    when they're individually too small — so a strip of tiny panels becomes one comfortable zoom.
 *  - **Divide** oversized panels, using the panel's size *relative to the page*:
 *      - A panel **as wide as the page** but not tall → split into **2** side-by-side pieces.
 *      - A panel **as wide as the page and tall ("broad")** → split into **4** (a 2×2 grid).
 *      - A **double-page spread** (landscape image) with a panel spanning both pages → split at the
 *        page seam into two halves, then apply the 2/4 rule to each half → **up to 8** pieces.
 *      - Other big elongated panels keep the classic single cut (wide → vertical, tall → horizontal).
 *      - Square / normal panels are left as-is.
 *    Cuts are placed in the gap between speech-bubble groups when bubbles are available, else centred.
 *
 * Works in normalized `[0,1]` coordinates; real pixel aspect uses the page dimensions, since a
 * normalized square isn't a real square on a non-square page.
 */
object PanelPlanner {

    data class Config(
        /** Panel area (fraction of page) below which it's "small" → a merge candidate. */
        val smallAreaFraction: Float = 0.10f,
        /** Panel area (fraction of page) above which it's "big" → a divide candidate. */
        val bigAreaFraction: Float = 0.35f,
        /** Pixel aspect (w/h) within [low, high] counts as square → never divided (when not full-width). */
        val squareAspectLow: Float = 0.8f,
        val squareAspectHigh: Float = 1.25f,
        /** Max normalized gap between two panels for them to count as adjacent. */
        val adjacencyGap: Float = 0.05f,
        /** Min perpendicular overlap (fraction of the shorter side) for adjacency. */
        val adjacencyOverlap: Float = 0.4f,
        /** Hard cap on how many small panels merge into one zoom (readability). */
        val maxMergeCount: Int = 3,
        /** Whether oversized panels get split into multiple stops at all (see class doc). */
        val enableDivide: Boolean = true,
        /** A merged group may not exceed these fractions of the page (keeps the zoom readable). */
        val maxMergedWidthFraction: Float = 0.55f,
        val maxMergedHeightFraction: Float = 0.45f,
        /** A divide cut must fall within this central band of the panel (avoids lopsided cuts). */
        val cutCentralMin: Float = 0.30f,
        val cutCentralMax: Float = 0.70f,
        // --- ratio-based division ---
        /** Panel width ≥ this fraction of the page ⇒ "as wide as the page". */
        val fullWidthFraction: Float = 0.85f,
        /** Panel height ≥ this fraction of the page ⇒ "broad" (split into 4 rather than 2). */
        val broadHeightFraction: Float = 0.55f,
        /** Don't split a full-width panel thinner than this (avoids slicing thin banners). */
        val minDivideHeightFraction: Float = 0.10f,
        /** Image aspect (w/h) ≥ this ⇒ treat the page image as a double-page spread. Two portrait
         *  pages side by side give ≈1.3 (2× a ~0.65 single-page aspect), so the cutoff sits below
         *  that — anything clearly wider than tall is a spread. */
        val spreadAspectMin: Float = 1.15f,
        /** On a spread, a panel this wide (fraction of the image) is treated as spanning both pages. */
        val crossPageWidthFraction: Float = 0.85f,
        /** On a spread, a panel at least this wide (≈ one page-half) is "full width of its own page",
         *  so a big per-page panel on a wide spread is broken into more parts instead of left whole. */
        val spreadPageWidthFraction: Float = 0.42f,
    ) {
        companion object {
            /**
             * Manga profile: manga is paced panel-by-panel — small reaction/dialogue beats are
             * narratively distinct, and the reader wants to land on each rather than have a strip of
             * them merged into one zoom. So only *truly tiny* panels merge, and at most two at a time
             * (vs. the Western-comic default that fuses runs of up to three ~10%-of-page panels).
             * Division is off entirely: a detected panel — however big, wide, or tall — is exactly
             * one stop, matching how a reader actually experiences a manga panel (one beat, one tap),
             * rather than being carved into 2-8 pieces by a geometric wide/broad/spread heuristic.
             */
            val MANGA = Config(
                smallAreaFraction = 0.05f,
                maxMergeCount = 2,
                enableDivide = false,
            )
        }
    }

    private data class Region(val panel: PanelRect, val merged: Boolean)

    /** Direction a merge run is growing in, so a group stays a single row or column (no L-shapes). */
    private enum class Dir { NONE, HORIZONTAL, VERTICAL }

    fun plan(
        ordered: List<PanelRect>,
        bubbles: List<PanelRect>,
        pageW: Int,
        pageH: Int,
        rightToLeft: Boolean,
        config: Config = Config(),
    ): List<PanelRect> {
        if (ordered.isEmpty()) return ordered
        val pageAspect = pageW.toFloat() / pageH.toFloat()

        val merged = mergeSmall(ordered, config)
        val result = ArrayList<PanelRect>(merged.size)
        for (region in merged) {
            val p = region.panel
            if (config.enableDivide && !region.merged && shouldDivide(p, pageAspect, config)) {
                result += divide(p, bubbles, pageAspect, rightToLeft, config)
            } else {
                result += p
            }
        }
        return result
    }

    private fun mergeSmall(ordered: List<PanelRect>, config: Config): List<Region> {
        val regions = ArrayList<Region>()
        var i = 0
        while (i < ordered.size) {
            val cur = ordered[i]
            if (!isSmall(cur, config)) {
                regions += Region(cur, merged = false)
                i++
                continue
            }
            var union = cur
            var j = i
            var count = 1
            var dir = Dir.NONE
            while (j + 1 < ordered.size && count < config.maxMergeCount && isSmall(ordered[j + 1], config)) {
                val next = ordered[j + 1]
                val stepDir = adjacencyDir(ordered[j], next, config)
                if (stepDir == Dir.NONE) break
                if (dir == Dir.NONE) dir = stepDir else if (stepDir != dir) break
                val candidate = union(union, next)
                if (candidate.width > config.maxMergedWidthFraction ||
                    candidate.height > config.maxMergedHeightFraction
                ) break
                union = candidate
                j++
                count++
            }
            regions += Region(union, merged = count > 1)
            i = j + 1
        }
        return regions
    }

    /** Whether a (non-merged) panel should be split, and which rule applies, is decided here. */
    private fun shouldDivide(p: PanelRect, pageAspect: Float, c: Config): Boolean {
        val spread = isSpread(pageAspect, c)
        if (spread && isCrossPage(p, c)) return true
        if (isFullWidth(p, c, spread) && p.height >= c.minDivideHeightFraction) return true
        return isBig(p, c) && !isSquare(p, pageAspect, c)
    }

    private fun divide(
        panel: PanelRect,
        bubbles: List<PanelRect>,
        pageAspect: Float,
        rightToLeft: Boolean,
        config: Config,
    ): List<PanelRect> {
        // Double-page spread: the two pages are one merged image, so a panel spanning them is read
        // row by row across the full width — a piece on the left page and one at the same height on
        // the right page are consecutive, NOT "whole right page then whole left page". Split into a
        // 4-column grid (≈2 columns per page), with a second row when it's also tall, emitted in
        // reading order (top→bottom, each row left→right or right→left).
        if (isSpread(pageAspect, config) && isCrossPage(panel, config)) {
            val rows = if (panel.height >= config.broadHeightFraction) 2 else 1
            return gridSplit(panel, cols = 4, rows = rows, rightToLeft)
        }
        // A panel as wide as the page (or, on a spread, as wide as one page) → 2 (just wide) or 4 (broad).
        if (isFullWidth(panel, config, isSpread(pageAspect, config))) {
            return splitByBreadth(panel, bubbles, rightToLeft, config)
        }
        // Other big, elongated, non-square panels keep the classic single cut.
        return classicDivide(panel, bubbles, pageAspect, rightToLeft, config)
    }

    /**
     * Splits [panel] into a [rows]×[cols] grid with even cuts, emitted row by row (top→bottom) and
     * within each row in reading direction (left→right, or right→left for [rtl]). Used for a wide
     * spread panel so the merged two pages read as one across the seam.
     */
    private fun gridSplit(panel: PanelRect, cols: Int, rows: Int, rtl: Boolean): List<PanelRect> {
        val cw = panel.width / cols
        val rh = panel.height / rows
        val pieces = ArrayList<PanelRect>(rows * cols)
        for (r in 0 until rows) {
            val top = if (r == 0) panel.top else panel.top + r * rh
            val bottom = if (r == rows - 1) panel.bottom else panel.top + (r + 1) * rh
            val colOrder = if (rtl) (cols - 1 downTo 0) else (0 until cols)
            for (c in colOrder) {
                val left = if (c == 0) panel.left else panel.left + c * cw
                val right = if (c == cols - 1) panel.right else panel.left + (c + 1) * cw
                pieces += PanelRect(left, top, right, bottom)
            }
        }
        return pieces
    }

    /** Full-width region → 4 pieces (2×2) if broad (tall too), else 2 pieces (side-by-side). */
    private fun splitByBreadth(panel: PanelRect, bubbles: List<PanelRect>, rtl: Boolean, config: Config): List<PanelRect> {
        val inside = bubblesInside(panel, bubbles)
        return if (panel.height >= config.broadHeightFraction) {
            quarter(panel, inside, rtl, config)
        } else {
            halveLeftRight(panel, inside, rtl, config)
        }
    }

    /** Two side-by-side pieces (one vertical, bubble-aware cut). */
    private fun halveLeftRight(panel: PanelRect, inside: List<PanelRect>, rtl: Boolean, config: Config): List<PanelRect> {
        val cut = cutPosition(inside.map { it.left }, inside.map { it.right }, panel.left, panel.right, config)
        val left = PanelRect(panel.left, panel.top, cut, panel.bottom)
        val right = PanelRect(cut, panel.top, panel.right, panel.bottom)
        return if (rtl) listOf(right, left) else listOf(left, right)
    }

    /** Four pieces in a 2×2 grid (one vertical + one horizontal, bubble-aware cut). */
    private fun quarter(panel: PanelRect, inside: List<PanelRect>, rtl: Boolean, config: Config): List<PanelRect> {
        val vCut = cutPosition(inside.map { it.left }, inside.map { it.right }, panel.left, panel.right, config)
        val hCut = cutPosition(inside.map { it.top }, inside.map { it.bottom }, panel.top, panel.bottom, config)
        val tl = PanelRect(panel.left, panel.top, vCut, hCut)
        val tr = PanelRect(vCut, panel.top, panel.right, hCut)
        val bl = PanelRect(panel.left, hCut, vCut, panel.bottom)
        val br = PanelRect(vCut, hCut, panel.right, panel.bottom)
        // Read each row in reading order, top row then bottom row.
        return if (rtl) listOf(tr, tl, br, bl) else listOf(tl, tr, bl, br)
    }

    /** The original behavior for big, elongated, non-full-width panels: one cut → 2 pieces. */
    private fun classicDivide(
        panel: PanelRect,
        bubbles: List<PanelRect>,
        pageAspect: Float,
        rtl: Boolean,
        config: Config,
    ): List<PanelRect> {
        val inside = bubblesInside(panel, bubbles)
        return if (realAspect(panel, pageAspect) >= config.squareAspectHigh) {
            halveLeftRight(panel, inside, rtl, config) // wide → vertical cut
        } else {
            val cut = cutPosition(inside.map { it.top }, inside.map { it.bottom }, panel.top, panel.bottom, config)
            listOf(
                PanelRect(panel.left, panel.top, panel.right, cut),
                PanelRect(panel.left, cut, panel.right, panel.bottom),
            ) // tall → horizontal cut
        }
    }

    private fun bubblesInside(panel: PanelRect, bubbles: List<PanelRect>): List<PanelRect> =
        bubbles.filter { it.centerX in panel.left..panel.right && it.centerY in panel.top..panel.bottom }

    /**
     * Picks the cut coordinate along an axis: the midpoint of the largest gap between consecutive
     * bubble spans, clamped to the central band; falls back to the centre when there's no usable gap.
     */
    private fun cutPosition(
        lowEdges: List<Float>,
        highEdges: List<Float>,
        start: Float,
        end: Float,
        config: Config,
    ): Float {
        val center = (start + end) / 2f
        val lo = start + (end - start) * config.cutCentralMin
        val hi = start + (end - start) * config.cutCentralMax

        if (lowEdges.size >= 2) {
            val spans = lowEdges.indices.map { lowEdges[it] to highEdges[it] }.sortedBy { it.first }
            var bestGap = 0f
            var bestMid = center
            var cursor = spans.first().second
            for (k in 1 until spans.size) {
                val (nextLow, nextHigh) = spans[k]
                val gap = nextLow - cursor
                if (gap > bestGap) {
                    bestGap = gap
                    bestMid = (cursor + nextLow) / 2f
                }
                cursor = maxOf(cursor, nextHigh)
            }
            if (bestGap > 0f && bestMid in lo..hi) return bestMid
        }
        return center.coerceIn(lo, hi)
    }

    private fun isSmall(p: PanelRect, c: Config) = p.area < c.smallAreaFraction
    private fun isBig(p: PanelRect, c: Config) = p.area > c.bigAreaFraction
    private fun isFullWidth(p: PanelRect, c: Config, spread: Boolean) =
        p.width >= if (spread) c.spreadPageWidthFraction else c.fullWidthFraction
    private fun isSpread(pageAspect: Float, c: Config) = pageAspect >= c.spreadAspectMin
    private fun isCrossPage(p: PanelRect, c: Config) = p.width >= c.crossPageWidthFraction

    private fun realAspect(p: PanelRect, pageAspect: Float): Float =
        if (p.height <= 0f) 1f else (p.width / p.height) * pageAspect

    private fun isSquare(p: PanelRect, pageAspect: Float, c: Config): Boolean {
        val a = realAspect(p, pageAspect)
        return a in c.squareAspectLow..c.squareAspectHigh
    }

    private fun adjacencyDir(a: PanelRect, b: PanelRect, c: Config): Dir {
        val vOverlap = overlap(a.top, a.bottom, b.top, b.bottom) / minOf(a.height, b.height).coerceAtLeast(1e-4f)
        val hOverlap = overlap(a.left, a.right, b.left, b.right) / minOf(a.width, b.width).coerceAtLeast(1e-4f)
        val hGap = (maxOf(a.left, b.left) - minOf(a.right, b.right)).coerceAtLeast(0f)
        val vGap = (maxOf(a.top, b.top) - minOf(a.bottom, b.bottom)).coerceAtLeast(0f)
        val sideBySide = vOverlap >= c.adjacencyOverlap && hGap <= c.adjacencyGap
        val stacked = hOverlap >= c.adjacencyOverlap && vGap <= c.adjacencyGap
        return when {
            sideBySide && stacked -> if (hGap <= vGap) Dir.HORIZONTAL else Dir.VERTICAL
            sideBySide -> Dir.HORIZONTAL
            stacked -> Dir.VERTICAL
            else -> Dir.NONE
        }
    }

    private fun overlap(a0: Float, a1: Float, b0: Float, b1: Float): Float =
        (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0f)

    private fun union(a: PanelRect, b: PanelRect): PanelRect =
        PanelRect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
}
