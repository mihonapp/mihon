package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelPipelineTest {

    @Test
    fun pageWithNoDetectedPanelsIsShownWhole() {
        // When the model returns nothing, there's no real panel geometry to zoom into — show the
        // whole page as a single stop rather than guessing at a geometric split.
        val regions = PanelPipeline.zoomRegions(emptyList(), emptyList(), 1000, 1500, rightToLeft = false)
        assertEquals(listOf(PanelRect.FULL_PAGE), regions)
    }

    @Test
    fun mangaPageWithOnlyOneWeakDetectionIsShownWhole() {
        // A single region covering most of the page (e.g. a background too noisy for the model to
        // resolve the real panel boundaries) isn't real panel geometry either — same as the
        // no-detection case, fall back to the whole page rather than a geometric split.
        val weakDetection = listOf(PanelRect(0.07f, 0.0f, 1.0f, 0.86f))
        val regions = PanelPipeline.zoomRegions(weakDetection, emptyList(), 1000, 1500, rightToLeft = true)
        assertEquals(listOf(PanelRect.FULL_PAGE), regions)
    }

    @Test
    fun mangaPageWithTwoConfidentPanelsIsNotDivided() {
        // The actual complaint this profile exists to fix: two real, separately-detected panels
        // must each stay exactly one stop, not get split further.
        val panels = listOf(PanelRect(0.0f, 0.0f, 1.0f, 0.45f), PanelRect(0.0f, 0.5f, 1.0f, 1.0f))
        val regions = PanelPipeline.zoomRegions(panels, emptyList(), 1000, 1500, rightToLeft = true)
        assertEquals(2, regions.size)
    }

    @Test
    fun panelWithNoOverflowingBubbleOnlyGetsTheBaseMargin() {
        // Both panels are deliberately >10% area so neither is a merge candidate, keeping this a
        // pure padding test rather than exercising the planner's merge/divide behavior too.
        val panel = PanelRect(0.10f, 0.10f, 0.45f, 0.45f)
        val bubble = PanelRect(0.20f, 0.20f, 0.30f, 0.30f) // fully inside, nowhere near an edge
        val other = PanelRect(0.60f, 0.60f, 0.95f, 0.95f)
        val region = PanelPipeline.zoomRegions(listOf(panel, other), listOf(bubble), 1000, 1000, false)
            .first { it.left < 0.15f }
        // Base margin only: 5.7% of the panel's own 0.35-wide size ≈ 0.02, well short of the bubble.
        assertTrue(region.left in 0.075f..0.085f, "expected only the base margin, got $region")
    }

    @Test
    fun panelWithAnOverflowingBubbleGrowsToContainIt() {
        val panel = PanelRect(0.30f, 0.30f, 0.65f, 0.65f)
        // Centred inside the panel (so it's attributed to it) but bleeding past its left edge.
        val bubble = PanelRect(0.22f, 0.40f, 0.42f, 0.55f)
        val other = PanelRect(0.02f, 0.70f, 0.37f, 1.0f)
        val region = PanelPipeline.zoomRegions(listOf(panel, other), listOf(bubble), 1000, 1000, false)
            .first { it.left < 0.2f }
        assertTrue(region.left < bubble.left, "expected the panel to grow past the bubble's left edge: $region")
    }

    @Test
    fun pipelineMatchesOrderingThenPlanning() {
        val shuffled = listOf(
            PanelRect(0.55f, 0.5f, 1.0f, 1.0f),
            PanelRect(0.0f, 0.0f, 0.45f, 0.4f),
            PanelRect(0.0f, 0.5f, 0.45f, 1.0f),
            PanelRect(0.55f, 0.0f, 1.0f, 0.4f),
        )
        val bubbles = listOf(PanelRect(0.1f, 0.1f, 0.2f, 0.2f))

        val viaPipeline = PanelPipeline.zoomRegions(shuffled, bubbles, 1000, 1500, rightToLeft = false)
        val manual = PanelPlanner.plan(
            PanelOrdering.order(shuffled, rightToLeft = false),
            bubbles, 1000, 1500, rightToLeft = false,
        )
        // The pipeline is ordering → planning → a small outward pad (breathing room for overflowing
        // text). So it must keep the same count and order, and each region must CONTAIN its planned
        // counterpart (padding only grows, clamped to the page) — never shrink or reorder.
        assertEquals(manual.size, viaPipeline.size, "pipeline should preserve the planned region count")
        for (i in manual.indices) {
            val m = manual[i]
            val v = viaPipeline[i]
            assertTrue(
                v.left <= m.left + 1e-4f && v.top <= m.top + 1e-4f &&
                    v.right >= m.right - 1e-4f && v.bottom >= m.bottom - 1e-4f,
                "pipeline region $i should be a padded superset of the planned region: $v vs $m",
            )
        }
    }
}
