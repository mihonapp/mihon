package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Pure position/direction lookups over a [PagerViewerAdapter]'s flat item list, extracted from
 * [PagerViewer] so the spread cases (where the page the reader cares about is nested inside a
 * [PageSpread] rather than being a top-level item) can be unit-tested without a live pager. Each
 * function is a plain query over the item list; the viewer keeps the actual `setCurrentItem`/preload
 * side effects.
 */
object PagerNavigation {

    /**
     * The page a spread is tracked by: its later, higher-index page. Reaching a spread means the
     * reader has effectively reached that page. A lone [ReaderPage] maps to itself; anything else
     * (a [eu.kanade.tachiyomi.ui.reader.model.ChapterTransition]) has no comparable page.
     */
    fun comparablePage(item: Any?): ReaderPage? = item as? ReaderPage ?: (item as? PageSpread)?.secondPage

    /**
     * A spread's two pages in on-screen left-to-right order: in L2R the first (lower-index) page sits
     * on the left; in R2L it sits on the right. The single source of truth for the side↔page mapping:
     * [PagerSpreadHolder] lays the halves out in this order, and a per-half page action reads it back.
     */
    fun screenOrder(spread: PageSpread, isL2r: Boolean): Pair<ReaderPage, ReaderPage> =
        if (isL2r) spread.firstPage to spread.secondPage else spread.secondPage to spread.firstPage

    /**
     * The leading page of an item: a lone page is itself; a spread reduces to its first page. This is
     * the reading position to keep when the pairing shifts: after a shift the first page may stand
     * alone, so tracking the later page ([comparablePage]) would skip past it onto the next spread.
     */
    fun leadingPage(item: Any?): ReaderPage? = item as? ReaderPage ?: (item as? PageSpread)?.firstPage

    /**
     * Position of [page] in [items] whether it's a top-level item or nested as either half of a
     * [PageSpread]; -1 if absent. Matched by reference, the same [ReaderPage] instances the adapter
     * holds. Backs slider/tap navigation, which hands over a page that pairing may have folded into
     * a spread.
     */
    fun positionOfPage(items: List<Any>, page: ReaderPage): Int =
        items.indexOfFirst {
            it === page || (it is PageSpread && (it.firstPage === page || it.secondPage === page))
        }

    /**
     * Position of a relocation [anchor] captured before a list rebuild; -1 if absent. A
     * [eu.kanade.tachiyomi.ui.reader.model.ChapterTransition] is matched structurally (a rebuild
     * constructs fresh instances, so its own `equals` is what finds it); a [ReaderPage] is matched
     * by reference, standalone or as either half of a [PageSpread] it may have been regrouped into.
     */
    fun positionOfAnchor(items: List<Any>, anchor: Any): Int =
        items.indexOfFirst {
            it == anchor ||
                (anchor is ReaderPage && it is PageSpread && (it.firstPage === anchor || it.secondPage === anchor))
        }

    /**
     * Whether moving from [previous] to [current] (both already reduced to their [comparablePage])
     * is a forward turn. Two pages sharing a number are a split page and its [InsertPage]; the
     * insert always reads second, so it counts as forward. With no previous page to compare, a move
     * that came from a "previous chapter" transition reads backward; everything else forward.
     */
    fun isForwardTurn(previous: ReaderPage?, current: ReaderPage?, cameFromPrevTransition: Boolean): Boolean = when {
        previous != null && current != null ->
            if (current.number == previous.number) current is InsertPage else current.number > previous.number
        cameFromPrevTransition && current != null -> false
        else -> true
    }

    /** What the viewer does with its pager after a chapter-window (re)build. */
    enum class LayoutAction { HOLD, LAYOUT_FIRST, RELOCATE }

    /**
     * Decides that action from the two facts that matter, so the rule is checkable without a live
     * pager: **HOLD**: keep the pager hidden (the reader's loading state) whenever the current
     * chapter's spread offset is still being detected, on any entry path (fresh open, boundary
     * cross, chapter-forward), so a chapter is never displayed at a pairing that would then reflow;
     * otherwise **LAYOUT_FIRST** for a fresh (gone) window, or **RELOCATE** to follow the anchor on a
     * live rebuild. The hold precedes the gone/live split deliberately: a chapter reached by
     * chapter-forward arrives on the live path, and must hold there just the same.
     */
    fun layoutAction(isGone: Boolean, isAwaitingDetection: Boolean): LayoutAction = when {
        isAwaitingDetection -> LayoutAction.HOLD
        isGone -> LayoutAction.LAYOUT_FIRST
        else -> LayoutAction.RELOCATE
    }
}
