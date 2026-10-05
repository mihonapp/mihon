package tachiyomi.core.common.util.system.panel

import kotlin.math.max
import kotlin.math.min

/** Orders detected panels into reading order. Pure Kotlin, no Android dependencies. */
object PanelOrder {

    /** Minimum overlap, as a fraction of the smaller extent, for two panels to share a row or a column. */
    private const val OVERLAP_RATIO = 0.5f

    /**
     * Sorts [panels] into reading order.
     *
     * Row rule:
     * 1. Panels are sorted by `top` and grouped greedily into rows. A panel joins the current row when its
     *    vertical overlap with the row's union span is at least 50% of the smaller of the two heights;
     *    otherwise it starts a new row. Rows are ordered by their union `top`.
     * 2. Inside a row, panels are grouped into columns with the same 50% rule applied horizontally, walking
     *    in reading direction (`left` ascending for LTR, `right` descending for RTL). Columns follow
     *    reading direction and the panels of a column run top to bottom. This keeps a tall panel next to a
     *    stack of panels in order: LTR gives tall, then the stack; RTL gives the stack, then tall.
     * 3. Rows are flattened in order.
     *
     * Spreads: when [isSpread] is true (an unsplit double page), panels are assigned to the left or right
     * half by `centerX < 0.5`. The half that comes first in reading direction (left for LTR, right for RTL)
     * is sorted with the rules above and emitted completely before the other half.
     */
    fun sort(panels: List<PanelRect>, rightToLeft: Boolean, isSpread: Boolean = false): List<PanelRect> {
        if (panels.size < 2) return panels.toList()
        if (!isSpread) return sortHalf(panels, rightToLeft)
        val (left, right) = panels.partition { it.centerX < 0.5f }
        val first = if (rightToLeft) right else left
        val second = if (rightToLeft) left else right
        return sortHalf(first, rightToLeft) + sortHalf(second, rightToLeft)
    }

    private fun sortHalf(panels: List<PanelRect>, rightToLeft: Boolean): List<PanelRect> {
        if (panels.size < 2) return panels.toList()
        val byTop = panels.sortedWith(compareBy<PanelRect> { it.top }.thenBy { it.left })
        val rows = mutableListOf<MutableList<PanelRect>>()
        var rowTop = 0f
        var rowBottom = 0f
        for (panel in byTop) {
            val current = rows.lastOrNull()
            if (current != null && overlaps(panel.top, panel.bottom, rowTop, rowBottom)) {
                current += panel
                rowTop = min(rowTop, panel.top)
                rowBottom = max(rowBottom, panel.bottom)
            } else {
                rows += mutableListOf(panel)
                rowTop = panel.top
                rowBottom = panel.bottom
            }
        }
        return rows.flatMap { sortRow(it, rightToLeft) }
    }

    private fun sortRow(row: List<PanelRect>, rightToLeft: Boolean): List<PanelRect> {
        if (row.size < 2) return row
        val ordered = if (rightToLeft) {
            row.sortedWith(compareByDescending<PanelRect> { it.right }.thenBy { it.top })
        } else {
            row.sortedWith(compareBy<PanelRect> { it.left }.thenBy { it.top })
        }
        val columns = mutableListOf<MutableList<PanelRect>>()
        var colLeft = 0f
        var colRight = 0f
        for (panel in ordered) {
            val current = columns.lastOrNull()
            if (current != null && overlaps(panel.left, panel.right, colLeft, colRight)) {
                current += panel
                colLeft = min(colLeft, panel.left)
                colRight = max(colRight, panel.right)
            } else {
                columns += mutableListOf(panel)
                colLeft = panel.left
                colRight = panel.right
            }
        }
        return columns.flatMap { column -> column.sortedBy { it.top } }
    }

    private fun overlaps(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Boolean {
        val overlap = min(aEnd, bEnd) - max(aStart, bStart)
        if (overlap <= 0f) return false
        return overlap >= OVERLAP_RATIO * min(aEnd - aStart, bEnd - bStart)
    }
}
