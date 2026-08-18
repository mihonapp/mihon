package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.max
import kotlin.math.min

/**
 * The full shared post-detection pipeline in one call: raw detected boxes go in, final zoom
 * regions in reading order come out.
 */
object PanelPipeline {
    /**
     * Baseline breathing room: every panel is grown by this fraction of its own size on every
     * side regardless of bubble overflow, so the reader shows a little context instead of hugging
     * the detected box edge-to-edge even on an ordinary panel with no overflowing bubble.
     */
    private const val BASE_MARGIN = 0.057f

    /**
     * Once a panel is grown to fully contain an overflowing bubble, this much further clearance
     * (a fraction of the bubble's own size) is added so the bubble's own edge isn't touching the
     * screen edge.
     */
    private const val BUBBLE_CLEARANCE = 0.06f

    fun zoomRegions(
        panels: List<PanelRect>,
        bubbles: List<PanelRect>,
        pageW: Int,
        pageH: Int,
        rightToLeft: Boolean,
    ): List<PanelRect> {
        // Add any large, roughly-rectangular region the model left uncovered as a panel, so missed
        // panels get numbered too — then order and plan as usual.
        // Manga (read right-to-left) is paced panel-by-panel, so it uses a profile that merges far
        // less aggressively; Western LTR comics keep the default grid-friendly merging.
        val config = if (rightToLeft) PanelPlanner.Config.MANGA else PanelPlanner.Config()
        val filled = PanelGapFiller.fill(panels)
        val ordered = PanelOrdering.order(filled, rightToLeft)
        val planned = PanelPlanner.plan(ordered, bubbles, pageW, pageH, rightToLeft, config)
        if (planned.size >= 2) return pad(planned, bubbles)
        // Detection found too little to work with (nothing, or one region covering most of the
        // page — a background too noisy/textured for the model to resolve real panel boundaries on
        // is a common cause). There's no real panel geometry here to zoom into, so show the whole
        // page rather than guessing at a geometric split (panning through quarters of a page that's
        // actually one panel, or a busy background, reads as broken rather than helpful).
        return listOf(PanelRect.FULL_PAGE)
    }

    /**
     * Grows each panel by [BASE_MARGIN] of its own size per side, then further — only as far as
     * actually needed — to fully contain any speech bubble that belongs to it (its centre falls
     * inside the panel) but bleeds past the panel's own tight detected edge, which real bubbles
     * routinely do. A flat margin sized to cover the worst overflowing bubble would waste zoom on
     * every ordinary panel that has none at all; this only grows a panel that actually needs it,
     * and only by as much as that specific bubble needs.
     */
    private fun pad(panels: List<PanelRect>, bubbles: List<PanelRect>): List<PanelRect> = panels.map { p ->
        var left = p.left - p.width * BASE_MARGIN
        var top = p.top - p.height * BASE_MARGIN
        var right = p.right + p.width * BASE_MARGIN
        var bottom = p.bottom + p.height * BASE_MARGIN

        for (b in bubbles) {
            if (b.centerX !in p.left..p.right || b.centerY !in p.top..p.bottom) continue
            val clearanceX = b.width * BUBBLE_CLEARANCE
            val clearanceY = b.height * BUBBLE_CLEARANCE
            left = min(left, b.left - clearanceX)
            top = min(top, b.top - clearanceY)
            right = max(right, b.right + clearanceX)
            bottom = max(bottom, b.bottom + clearanceY)
        }

        PanelRect(left.coerceAtLeast(0f), top.coerceAtLeast(0f), right.coerceAtMost(1f), bottom.coerceAtMost(1f))
    }
}
