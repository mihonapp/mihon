package eu.kanade.tachiyomi.ui.reader.viewer.panel

/**
 * Sorts detected panels into reading order using a **recursive X-Y cut with straddle tolerance**.
 *
 * Comics are read in rows (top→bottom), and within a row left→right (Western) or right→left (manga).
 * At each step we look for a straight cut that separates the panels into two groups:
 *  - a **horizontal** cut (preferred) stacks the groups top→bottom — i.e. rows;
 *  - a **vertical** cut arranges them by reading direction — i.e. columns.
 * A panel is allowed to poke slightly across a cut (up to [STRADDLE_TOLERANCE] of its own length) and
 * is assigned to whichever side holds most of it — so a row of not-perfectly-aligned panels still
 * splits into one row. A panel that genuinely spans two rows (a tall panel beside stacked ones) makes
 * the horizontal cut impossible, so we cut vertically first — separating the tall panel from the
 * stack — then recurse, which is exactly the case a naive "group by row overlap" gets wrong.
 */
object PanelOrdering {

    /** A panel may cross a cut line by up to this fraction of its own height/width and still be
     *  assigned cleanly to one side (handles slightly misaligned panels in the same row). */
    private const val STRADDLE_TOLERANCE = 0.25f

    /** In the no-clean-cut fallback, panels whose tops are within this fraction of the page height
     *  are treated as one row (so a vertically-staggered row still reads left→right). */
    private const val ROW_BAND = 0.12f

    fun order(panels: List<PanelRect>, rightToLeft: Boolean = false): List<PanelRect> {
        if (panels.size <= 1) return panels
        return cut(panels, rightToLeft)
    }

    private fun cut(panels: List<PanelRect>, rightToLeft: Boolean): List<PanelRect> {
        if (panels.size <= 1) return panels

        // Prefer a horizontal cut → rows, read top to bottom.
        findCut(panels, vertical = false)?.let { (top, bottom) ->
            return cut(top, rightToLeft) + cut(bottom, rightToLeft)
        }

        // Otherwise a vertical cut → columns, read by direction (right group first for manga).
        findCut(panels, vertical = true)?.let { (left, right) ->
            return if (rightToLeft) cut(right, rightToLeft) + cut(left, rightToLeft)
            else cut(left, rightToLeft) + cut(right, rightToLeft)
        }

        // No clean cut (a big panel overlaps everything, so neither axis splits). Best effort: cluster
        // into rows by top — tolerant of vertical stagger — then read rows top→bottom and within a row
        // by reading direction. This keeps a staggered bottom row (e.g. a panel beside a shorter one)
        // in left→right order instead of "whichever starts higher first".
        val byTop = panels.sortedBy { it.top }
        val rows = ArrayList<MutableList<PanelRect>>()
        for (p in byTop) {
            val row = rows.lastOrNull()
            if (row != null && p.top - row.first().top <= ROW_BAND) row.add(p) else rows.add(mutableListOf(p))
        }
        return rows.flatMap { row ->
            row.sortedWith(if (rightToLeft) compareByDescending { it.left } else compareBy { it.left })
        }
    }

    /**
     * Tries to split [panels] into two non-empty groups along one axis. [vertical] = false cuts
     * horizontally (returns top group, bottom group); true cuts vertically (left group, right group).
     * Scans candidate cut lines (panel trailing edges); a line is valid only if every panel sits on
     * one side or straddles by at most [STRADDLE_TOLERANCE] of its length. Returns null if no line works.
     */
    private fun findCut(panels: List<PanelRect>, vertical: Boolean): Pair<List<PanelRect>, List<PanelRect>>? {
        val start = { p: PanelRect -> if (vertical) p.left else p.top }
        val end = { p: PanelRect -> if (vertical) p.right else p.bottom }

        val maxEnd = panels.maxOf(end)
        val candidates = panels.map(end).distinct().sorted()
        for (line in candidates) {
            if (line >= maxEnd) continue // wouldn't leave anything below/right
            val first = mutableListOf<PanelRect>()  // top (or left) side
            val second = mutableListOf<PanelRect>() // bottom (or right) side
            var valid = true
            for (p in panels) {
                val s = start(p); val e = end(p)
                when {
                    e <= line -> first.add(p)
                    s >= line -> second.add(p)
                    else -> {
                        val len = (e - s).coerceAtLeast(1e-4f)
                        val crossDepth = minOf(e - line, line - s) // how far it pokes onto the thinner side
                        if (crossDepth / len > STRADDLE_TOLERANCE) { valid = false; break }
                        if (line - s >= e - line) first.add(p) else second.add(p) // majority side
                    }
                }
            }
            if (valid && first.isNotEmpty() && second.isNotEmpty()) return first to second
        }
        return null
    }
}
