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
 * The window matches Mihon's four adjacent images in the navigation direction, then keeps one
 * image behind for quick desktop direction reversal. Dual-page modes skip the other page in the
 * currently visible spread before counting the four images ahead. Pages are always emitted
 * nearest-first and RTL affects placement rather than logical page order.
 */
object PrefetchPolicy {
    const val AHEAD_PAGES = 4
    const val BEHIND_PAGES = 1

    fun unitPages(mode: ReadingMode): Int = if (mode.isDualPage) 2 else 1

    fun plan(
        pageCount: Int,
        selectedIndex: Int,
        mode: ReadingMode,
        direction: NavigationDirection,
    ): List<Int> {
        require(pageCount > 0) { "pageCount must be positive" }
        require(selectedIndex in 0 until pageCount) { "selectedIndex $selectedIndex out of $pageCount pages" }

        val visibleWidth = unitPages(mode)
        val step = if (direction == NavigationDirection.FORWARD) 1 else -1
        val firstAhead = selectedIndex + step * visibleWidth
        val ahead = (0 until AHEAD_PAGES)
            .map { firstAhead + step * it }
            .filter { it in 0 until pageCount }
        val behind = (1..BEHIND_PAGES)
            .map { selectedIndex - step * it }
            .filter { it in 0 until pageCount }
        return (ahead + behind).distinct()
    }
}
