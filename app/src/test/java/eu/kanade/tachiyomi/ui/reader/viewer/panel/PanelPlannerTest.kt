package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelPlannerTest {

    private val pageW = 1000
    private val pageH = 1500

    private fun plan(
        panels: List<PanelRect>,
        bubbles: List<PanelRect> = emptyList(),
        rightToLeft: Boolean = false,
    ) = PanelPlanner.plan(panels, bubbles, pageW, pageH, rightToLeft)

    @Test
    fun emptyInputPassesThrough() {
        assertEquals(emptyList<PanelRect>(), plan(emptyList()))
    }

    @Test
    fun normalPanelsAreLeftAsIs() {
        // Area ~0.2 each: neither small (<0.10) nor big (>0.35).
        val a = PanelRect(0.0f, 0.0f, 0.5f, 0.4f)
        val b = PanelRect(0.5f, 0.0f, 1.0f, 0.4f)
        assertEquals(listOf(a, b), plan(listOf(a, b)))
    }

    @Test
    fun adjacentSmallPanelsMergeIntoOneRegion() {
        // Three tiny side-by-side panels in one strip (area 0.03 each, gaps < 0.05).
        val p1 = PanelRect(0.00f, 0.0f, 0.15f, 0.2f)
        val p2 = PanelRect(0.17f, 0.0f, 0.32f, 0.2f)
        val p3 = PanelRect(0.34f, 0.0f, 0.49f, 0.2f)
        val result = plan(listOf(p1, p2, p3))
        assertEquals(1, result.size)
        assertEquals(PanelRect(0.00f, 0.0f, 0.49f, 0.2f), result.single())
    }

    @Test
    fun mergeStopsAtMaxMergeCount() {
        // Four tiny adjacent panels; default cap is 3, so the run chunks 3 + 1.
        val panels = (0 until 4).map { i ->
            PanelRect(i * 0.12f, 0.0f, i * 0.12f + 0.10f, 0.2f)
        }
        val result = plan(panels)
        assertEquals(2, result.size)
        assertEquals(panels[0].left, result[0].left)
        assertEquals(panels[2].right, result[0].right)
        assertEquals(panels[3], result[1])
    }

    @Test
    fun distantSmallPanelsDoNotMerge() {
        val p1 = PanelRect(0.0f, 0.0f, 0.15f, 0.2f)
        val p2 = PanelRect(0.5f, 0.0f, 0.65f, 0.2f) // gap 0.35 >> adjacencyGap
        assertEquals(listOf(p1, p2), plan(listOf(p1, p2)))
    }

    @Test
    fun bigWidePanelIsDividedWithVerticalCut() {
        // Area 0.5, pixel aspect (0.9/0.55)*(1000/1500) ≈ 1.09... make it clearly wide:
        // width 0.95, height 0.4 → area 0.38 > 0.35; aspect (0.95/0.4)*(2/3) ≈ 1.58 > 1.25.
        val wide = PanelRect(0.0f, 0.0f, 0.95f, 0.4f)
        val result = plan(listOf(wide))
        assertEquals(2, result.size)
        val (first, second) = result
        assertEquals(wide.left, first.left)
        assertEquals(wide.right, second.right)
        assertEquals(first.right, second.left) // pieces share the cut
        assertEquals(wide.top, first.top)
        assertEquals(wide.bottom, first.bottom)
    }

    @Test
    fun rightToLeftDivisionEmitsRightPieceFirst() {
        val wide = PanelRect(0.0f, 0.0f, 0.95f, 0.4f)
        val result = plan(listOf(wide), rightToLeft = true)
        assertEquals(2, result.size)
        assertTrue(result[0].left > result[1].left)
    }

    @Test
    fun bigSquarePanelIsTreatedAsSplashAndKept() {
        // Pixel aspect (0.75/0.5)*(1000/1500) = 1.0 → square; area 0.375 > 0.35 but never divided.
        val splash = PanelRect(0.1f, 0.2f, 0.85f, 0.7f)
        assertEquals(listOf(splash), plan(listOf(splash)))
    }

    @Test
    fun cutAvoidsBubblesByFallingInLargestGap() {
        val wide = PanelRect(0.0f, 0.0f, 0.95f, 0.4f)
        // Two bubble groups: one on the far left, one on the far right; gap centre ≈ 0.475.
        val bubbles = listOf(
            PanelRect(0.05f, 0.1f, 0.30f, 0.3f),
            PanelRect(0.65f, 0.1f, 0.90f, 0.3f),
        )
        val result = plan(listOf(wide), bubbles = bubbles)
        assertEquals(2, result.size)
        val cut = result[0].right
        assertEquals(0.475f, cut, 0.01f)
        // The cut must not slice through either bubble.
        for (bubble in bubbles) {
            assertTrue(cut <= bubble.left || cut >= bubble.right, "cut $cut slices bubble $bubble")
        }
    }

    @Test
    fun fullWidthBroadPanelSplitsIntoFour() {
        // Full width (0.96) AND broad/tall (0.75) → 2×2 grid, read TL, TR, BL, BR.
        val p = PanelRect(0.0f, 0.10f, 0.96f, 0.85f)
        val r = plan(listOf(p))
        assertEquals(4, r.size)
        // corners
        assertEquals(p.left, r[0].left); assertEquals(p.top, r[0].top) // TL
        assertEquals(p.right, r[1].right); assertEquals(p.top, r[1].top) // TR
        assertEquals(p.left, r[2].left); assertEquals(p.bottom, r[2].bottom) // BL
        assertEquals(p.right, r[3].right); assertEquals(p.bottom, r[3].bottom) // BR
        // shared cuts
        assertEquals(r[0].right, r[1].left) // vertical cut
        assertEquals(r[0].bottom, r[2].top) // horizontal cut
        assertEquals(r[1].bottom, r[3].top)
    }

    @Test
    fun fullWidthShortPanelStillSplitsIntoTwo() {
        // Full width (0.95) but short (0.30, not broad) → 2 side-by-side.
        val p = PanelRect(0.0f, 0.0f, 0.95f, 0.30f)
        val r = plan(listOf(p))
        assertEquals(2, r.size)
        assertEquals(r[0].right, r[1].left)
    }

    @Test
    fun doublePageBroadPanelReadsRowMajorAcrossBothPages() {
        // Landscape spread (aspect ≈1.47), a panel spanning both pages, broad → 4 cols × 2 rows = 8,
        // read as ONE image: the whole top row (across both pages) before the bottom row.
        val spread = PanelRect(0.0f, 0.10f, 1.0f, 0.85f)
        val r = PanelPlanner.plan(listOf(spread), emptyList(), 2200, 1500, false)
        assertEquals(8, r.size)
        val mid = (0.10f + 0.85f) / 2f
        assertTrue(r.take(4).all { it.bottom <= mid + 1e-3f }, "first 4 = top row (across both pages)")
        assertTrue(r.drop(4).all { it.top >= mid - 1e-3f }, "last 4 = bottom row")
        // The top row crosses the seam left→right (a left-page piece, then a right-page piece).
        assertTrue(r[0].left < r[1].left && r[1].left < r[2].left && r[2].left < r[3].left)
        assertTrue(r[0].right <= 0.5f + 1e-3f && r[3].left >= 0.5f - 1e-3f, "row spans both pages")
    }

    @Test
    fun doublePageShortPanelSplitsIntoFourAcross() {
        // Short cross-page panel → a single row of 4 across the full width, left→right.
        val spread = PanelRect(0.0f, 0.30f, 1.0f, 0.60f)
        val r = PanelPlanner.plan(listOf(spread), emptyList(), 2200, 1500, false)
        assertEquals(4, r.size)
        assertTrue(r[0].left < r[1].left && r[1].left < r[2].left && r[2].left < r[3].left, "left→right")
    }

    @Test
    fun doublePageRightToLeftReadsRightmostFirst() {
        val spread = PanelRect(0.0f, 0.30f, 1.0f, 0.60f)
        val r = PanelPlanner.plan(listOf(spread), emptyList(), 2200, 1500, true)
        assertEquals(4, r.size)
        assertTrue(r[0].left > r[1].left && r[1].left > r[2].left, "right→left across the spread")
        assertTrue(r[0].right >= 1.0f - 1e-3f, "starts at the far right")
    }

    @Test
    fun spreadPerPagePanelsAreEachBrokenUp() {
        // Spread (aspect ≈1.47) with one big panel per page. Neither spans both pages, but each fills
        // its own page → each split into 4 → 8 total.
        val leftPage = PanelRect(0.02f, 0.10f, 0.48f, 0.90f)
        val rightPage = PanelRect(0.52f, 0.10f, 0.98f, 0.90f)
        val r = PanelPlanner.plan(listOf(leftPage, rightPage), emptyList(), 2200, 1500, false)
        assertEquals(8, r.size)
    }

    @Test
    fun modestlyWideSpreadIsStillTreatedAsSpread() {
        // Two portrait pages side by side give aspect ≈1.3 (below the old 1.4 cutoff) → still a spread:
        // 4 cols × 2 rows = 8, read row-major (top row across both pages first).
        val spread = PanelRect(0.0f, 0.0f, 1.0f, 1.0f)
        val r = PanelPlanner.plan(listOf(spread), emptyList(), 3974, 3056, false)
        assertEquals(8, r.size)
        assertTrue(r.take(4).all { it.bottom <= 0.5f + 1e-3f }, "top row first")
        assertTrue(r.drop(4).all { it.top >= 0.5f - 1e-3f }, "bottom row second")
    }

    @Test
    fun perPageWidthRuleAppliesOnlyToSpreads() {
        // A half-page-wide, short panel: on a portrait page it's neither full-width nor big → kept
        // whole; on a landscape spread it fills one page → split into 2.
        val p = PanelRect(0.04f, 0.35f, 0.50f, 0.65f) // width 0.46, height 0.30
        assertEquals(1, PanelPlanner.plan(listOf(p), emptyList(), 1000, 1500, false).size) // portrait
        assertEquals(2, PanelPlanner.plan(listOf(p), emptyList(), 2200, 1500, false).size) // spread
    }

    @Test
    fun mangaProfileNeverDividesEvenAFullWidthBroadPanel() {
        // Same shape as fullWidthBroadPanelSplitsIntoFour, which the default profile quarters —
        // the manga profile must keep it as a single stop.
        val p = PanelRect(0.0f, 0.10f, 0.96f, 0.85f)
        val r = PanelPlanner.plan(listOf(p), emptyList(), pageW, pageH, false, PanelPlanner.Config.MANGA)
        assertEquals(listOf(p), r)
    }
}
