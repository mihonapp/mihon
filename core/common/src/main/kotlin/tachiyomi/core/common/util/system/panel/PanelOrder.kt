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
     * Layout rule, applied recursively to a block of panels:
     * 1. The block is cut into rows: panels are sorted by `top` and grouped greedily, a panel joining the
     *    current row when its vertical overlap with the row's union span is at least 50% of the smaller of
     *    the two heights. Several rows are read top to bottom, each one as a block of its own.
     * 2. A block that is a single row is cut into columns with the same 50% rule applied horizontally.
     *    Columns follow reading direction (`left` ascending for LTR, `right` descending for RTL) and each
     *    one is read as a block of its own. This keeps a tall panel next to a stack of panels in order:
     *    LTR gives tall, then the stack; RTL gives the stack, then tall.
     * 3. Neighbouring columns that line up as a grid (every row holds exactly one panel of each column)
     *    are read together, row by row. A tall panel next to a grid then gives tall, then the grid's first
     *    row, and so on, instead of running down the grid's first column.
     *
     * Spreads: when [isSpread] is true (an unsplit double page), panels are assigned to the left or right
     * half by `centerX < 0.5`. The half that comes first in reading direction (left for LTR, right for RTL)
     * is sorted with the rules above and emitted completely before the other half.
     */
    fun sort(panels: List<PanelRect>, rightToLeft: Boolean, isSpread: Boolean = false): List<PanelRect> {
        if (panels.size < 2) return panels.toList()
        if (!isSpread) return sortBlock(panels, rightToLeft)
        val (left, right) = panels.partition { it.centerX < 0.5f }
        val first = if (rightToLeft) right else left
        val second = if (rightToLeft) left else right
        return sortBlock(first, rightToLeft) + sortBlock(second, rightToLeft)
    }

    private fun sortBlock(panels: List<PanelRect>, rightToLeft: Boolean): List<PanelRect> {
        if (panels.size < 2) return panels.toList()
        val rows = rows(panels)
        if (rows.size > 1) return rows.flatMap { sortBlock(it, rightToLeft) }

        val columns = columns(panels, rightToLeft)
        if (columns.size < 2) {
            // Panels overlapping on both axes: no cut separates them.
            return panels.sortedWith(compareBy<PanelRect> { it.top }.thenBy { if (rightToLeft) -it.right else it.left })
        }
        val result = ArrayList<PanelRect>(panels.size)
        var start = 0
        while (start < columns.size) {
            var end = start + 1
            while (end < columns.size && isGrid(columns.subList(start, end + 1))) end++
            result += sortBlock(columns.subList(start, end).flatten(), rightToLeft)
            start = end
        }
        return result
    }

    /** True if [columns] cut into two or more rows that each hold exactly one panel of every column. */
    private fun isGrid(columns: List<List<PanelRect>>): Boolean {
        val rows = rows(columns.flatten())
        return rows.size > 1 && rows.all { row -> columns.all { column -> row.count { it in column } == 1 } }
    }

    private fun rows(panels: List<PanelRect>): List<List<PanelRect>> {
        return group(panels.sortedWith(compareBy<PanelRect> { it.top }.thenBy { it.left }), { it.top }, { it.bottom })
    }

    private fun columns(panels: List<PanelRect>, rightToLeft: Boolean): List<List<PanelRect>> {
        val ordered = if (rightToLeft) {
            panels.sortedWith(compareByDescending<PanelRect> { it.right }.thenBy { it.top })
        } else {
            panels.sortedWith(compareBy<PanelRect> { it.left }.thenBy { it.top })
        }
        return group(ordered, { it.left }, { it.right })
    }

    /** Greedily groups [ordered] panels whose `start..end` extent overlaps the current group's union extent. */
    private inline fun group(
        ordered: List<PanelRect>,
        start: (PanelRect) -> Float,
        end: (PanelRect) -> Float,
    ): List<List<PanelRect>> {
        val groups = mutableListOf<MutableList<PanelRect>>()
        var groupStart = 0f
        var groupEnd = 0f
        for (panel in ordered) {
            val current = groups.lastOrNull()
            if (current != null && overlaps(start(panel), end(panel), groupStart, groupEnd)) {
                current += panel
                groupStart = min(groupStart, start(panel))
                groupEnd = max(groupEnd, end(panel))
            } else {
                groups += mutableListOf(panel)
                groupStart = start(panel)
                groupEnd = end(panel)
            }
        }
        return groups
    }

    private fun overlaps(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Boolean {
        val overlap = min(aEnd, bEnd) - max(aStart, bStart)
        if (overlap <= 0f) return false
        return overlap >= OVERLAP_RATIO * min(aEnd - aStart, bEnd - bStart)
    }
}
