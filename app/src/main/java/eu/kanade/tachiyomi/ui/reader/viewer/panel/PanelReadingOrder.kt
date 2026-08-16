package eu.kanade.tachiyomi.ui.reader.viewer.panel

import kotlin.math.max
import kotlin.math.min

object PanelReadingOrder {

    private const val ROW_OVERLAP_THRESHOLD = 0.5f

    fun sort(rects: List<PanelRect>, direction: PanelDirection): List<PanelRect> {
        if (rects.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<PanelRect>>()
        for (rect in rects.sortedBy { it.top }) {
            val row = rows.lastOrNull { verticallyOverlaps(it, rect) }
            if (row != null) {
                row += rect
            } else {
                rows += mutableListOf(rect)
            }
        }

        return rows.flatMap { row ->
            val sorted = row.sortedBy { it.left }
            if (direction == PanelDirection.RTL) sorted.reversed() else sorted
        }
    }

    private fun verticallyOverlaps(row: List<PanelRect>, rect: PanelRect): Boolean {
        val rowTop = row.minOf { it.top }
        val rowBottom = row.maxOf { it.bottom }
        val overlapTop = max(rowTop, rect.top)
        val overlapBottom = min(rowBottom, rect.bottom)
        val overlap = (overlapBottom - overlapTop).coerceAtLeast(0f)
        val shorterHeight = min(rect.height, rowBottom - rowTop)
        return shorterHeight > 0f && overlap >= ROW_OVERLAP_THRESHOLD * shorterHeight
    }
}
