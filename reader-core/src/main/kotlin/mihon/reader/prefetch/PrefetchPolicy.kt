package mihon.reader.prefetch

import mihon.reader.model.ReadingMode

/** Logical navigation direction through the page order; RTL only remaps visuals, never this order. */
enum class NavigationDirection {
    FORWARD,
    BACKWARD,
}

/**
 * Computes which page indexes to prefetch around the selected page.
 *
 * The window is two logical units ahead in the navigation direction and one logical unit behind,
 * where a unit is one page for single/continuous modes and one two-page spread for dual modes.
 * Pages are emitted nearest-first: ahead pages in navigation order, then behind pages in
 * opposite order of distance. The currently visible spread is never part of the plan.
 */
object PrefetchPolicy {
    const val AHEAD_UNITS = 2
    const val BEHIND_UNITS = 1

    fun unitPages(mode: ReadingMode): Int = if (mode.isDualPage) 2 else 1

    fun plan(
        pageCount: Int,
        selectedIndex: Int,
        mode: ReadingMode,
        direction: NavigationDirection,
    ): List<Int> {
        require(pageCount > 0) { "pageCount must be positive" }
        require(selectedIndex in 0 until pageCount) { "selectedIndex $selectedIndex out of $pageCount pages" }

        val unit = unitPages(mode)
        val step = if (direction == NavigationDirection.FORWARD) 1 else -1
        val result = mutableListOf<Int>()

        // Ahead units start past the current unit, which the caller already displays.
        for (unitIndex in 1..AHEAD_UNITS) {
            val base = selectedIndex + step * unitIndex * unit
            val offsets = if (step > 0) (0 until unit) else (unit - 1 downTo 0)
            for (offset in offsets) {
                val index = base + offset
                if (index in 0 until pageCount) result += index
            }
        }
        // Behind units mirror the ahead ordering on the opposite side, nearest first.
        for (unitIndex in 1..BEHIND_UNITS) {
            val base = selectedIndex - step * unitIndex * unit
            val offsets = if (step > 0) (unit - 1 downTo 0) else (0 until unit)
            for (offset in offsets) {
                val index = base + offset
                if (index in 0 until pageCount) result += index
            }
        }
        return result.distinct()
    }
}
