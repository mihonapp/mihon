package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

// Naming in this package: `Pager*` classes are the pager view machinery (viewer, adapter, config,
// holders, navigation); `Spread*` classes are the spread feature's logic and services (this pairing
// math, the shift resolver, the scale calculator/coordinator, the offset sampler), usable independently
// of the pager view. The split is by role, not location: the `Spread*` files sit here because that is
// where the reader's paged code lives.

/**
 * Pure page-pairing logic for spread mode, extracted from [PagerViewerAdapter] so the grouping
 * rules (which are direction- and shift-sensitive) can be unit-tested without a running adapter
 * or viewer. The adapter keeps the guards (mode enabled,
 * horizontal pager) and supplies the viewer-derived facts (reading direction, shift state, the
 * current chapter) as plain parameters.
 */
object SpreadPairing {

    /**
     * The pairing shift to use for a chapter, by precedence: a [remembered] manual shift wins (the
     * reader's deliberate correction, including a deliberate "off"); else a [perMangaForce] the reader set
     * for this series; else the automatic stage: a [decisiveWide] interior spread, else the settled
     * classifier verdict [detected]; else the [default] baseline (a global preference). A deliberate user
     * choice always outranks the automatic stage, so a wide spread governs a chapter only until the reader
     * overrides it, leaving the manual toggle a live escape hatch even on a wide-governed chapter. Within
     * the automatic stage a genuine spread outranks the classifier's cue read. [remembered] is the
     * chapter's persisted `spread_shift` decoded to a parity (null when its DEFAULT: never toggled, or
     * cleared by a per-series change; see [SpreadShift.rememberedShift]); [perMangaForce] is the series'
     * explicit override (null when it inherits [default]); [decisiveWide] is the parity a wide two-page
     * spread pins (null when the feature is off or the chapter has no interior wide); [detected] is the
     * resolver's settled decision (null while still pending).
     */
    fun resolveShift(
        remembered: Boolean?,
        perMangaForce: Boolean?,
        decisiveWide: Boolean?,
        detected: Boolean?,
        default: Boolean,
    ): Boolean = remembered ?: perMangaForce ?: decisiveWide ?: detected ?: default

    /**
     * Pairs a flat, display-ordered run of a single chapter's pages into [PageSpread]s by a single running
     * parity: a page is a first-of-pair (paired with the next) or a second (shown solo when its partner is
     * missing), decided by its column `(index + wides-before-it + shift)`. [shifted] offsets every column
     * by one. An [InsertPage] or, when "treat wide pages as spreads" is on, an [isWide] page is emitted
     * solo and occupies a full pairing slot (two columns), so a wide two-page spread doesn't push the rest
     * of the chapter off by one, yet the parity flows across it: flipping [shifted] re-pairs both sides
     * of a wide, not only the run before it, so the manual shift toggle works from any position, as it
     * does with the feature off. This uninterrupted flow also governs the wide-regular-wide structural
     * inconsistency, an odd number of single pages between two wides: the one lone page it forces is not
     * re-aligned around, so the run past it reads as it would with the feature off rather than resetting the
     * pairing at the wide. Used per chapter by [PagerViewerAdapter.setChapters]. [isWide] defaults to
     * never-wide, leaving the pairing unchanged.
     */
    fun pairFlat(pages: List<ReaderPage>, shifted: Boolean, isWide: (ReaderPage) -> Boolean = { false }): List<Any> {
        val result = mutableListOf<Any>()
        val shiftBit = if (shifted) 1 else 0
        var wideCount = 0 // wide/insert pages passed so far; each is a slot the parity crosses
        var i = 0
        while (i < pages.size) {
            val page = pages[i]
            if (page is InsertPage || isWide(page)) {
                result.add(page)
                wideCount += 1
                i += 1
                continue
            }
            val firstOfPair = (i + wideCount + shiftBit) % 2 == 0
            val second = pages.getOrNull(i + 1)
            if (firstOfPair && second != null && second !is InsertPage && !isWide(second)) {
                result.add(PageSpread(page, second))
                i += 2
            } else {
                result.add(page)
                i += 1
            }
        }
        return result
    }

    /**
     * Pairs adjacent pages of a run already in *story* order (ascending page number), by the same running
     * parity as [pairFlat]: [shifted] offsets every column, a wide ([isWide]) or [InsertPage] is emitted
     * solo and the parity flows across it. Always builds [PageSpread] with the (lower, higher)
     * firstPage/secondPage convention; never pairs across a chapter boundary.
     */
    fun pairInStoryOrder(
        pages: List<ReaderPage>,
        shifted: Boolean,
        isWide: (ReaderPage) -> Boolean = { false },
    ): List<Any> {
        val result = mutableListOf<Any>()
        val shiftBit = if (shifted) 1 else 0
        var wideCount = 0
        var i = 0
        while (i < pages.size) {
            val a = pages[i]
            if (a is InsertPage || isWide(a)) {
                result.add(a)
                wideCount += 1
                i += 1
                continue
            }
            val firstOfPair = (i + wideCount + shiftBit) % 2 == 0
            val b = pages.getOrNull(i + 1)
            if (firstOfPair && b != null && b !is InsertPage && !isWide(b) && a.chapter == b.chapter) {
                result.add(PageSpread(a, b))
                i += 2
            } else {
                result.add(a)
                i += 1
            }
        }
        return result
    }

    /** Unwraps a spread into its two pages in *display* order for the given direction. */
    fun spreadOrder(spread: PageSpread, isR2L: Boolean): Pair<ReaderPage, ReaderPage> =
        if (isR2L) spread.secondPage to spread.firstPage else spread.firstPage to spread.secondPage

    /**
     * Which screen half a justified lone page sits on: its natural pairing column, so it abuts
     * the spine where its would-be partner would be (the recto/verso of a book), with the outer half
     * blank. [column] is the page's pairing column ([pairingColumnIndex]); [shifted] is that chapter's
     * parity shift. Returns true for the left half, false for the right; R2L mirrors L2R.
     */
    fun soloSideIsLeft(column: Int, shifted: Boolean, isR2L: Boolean): Boolean {
        // 0 = first page of a would-be pair, 1 = second. In L2R display the first sits on the left.
        val storyColumn = (column + if (shifted) 1 else 0) and 1
        return if (isR2L) storyColumn == 1 else storyColumn == 0
    }

    /**
     * [page]'s pairing column, which [soloSideIsLeft] needs to justify a lone page to its would-be
     * partner's side. Within a run this matches [pairFlat]'s running parity: a wide page is not a break
     * but a full pairing slot, so it counts as two columns while a normal page counts as one. A run breaks
     * at a transition, a chapter boundary, or an [InsertPage]: a split wide's half restarts pairing, so
     * unlike [pairFlat] (which counts an insert as a crossed slot) an insert restarts the run here. With no
     * inserts or wides this equals [ReaderPage.index].
     */
    fun pairingColumnIndex(items: List<Any>, page: ReaderPage, isWide: (ReaderPage) -> Boolean = { false }): Int {
        val pos = items.indexOf(page)
        if (pos < 0) return page.index
        var columns = 0
        for (i in pos - 1 downTo 0) {
            val prevPages = when (val prev = items[i]) {
                is InsertPage -> null // a split half, a run boundary
                is ReaderPage -> if (prev.chapter != page.chapter) null else listOf(prev)
                is PageSpread -> if (prev.firstPage.chapter !=
                    page.chapter
                ) {
                    null
                } else {
                    listOf(prev.firstPage, prev.secondPage)
                }
                else -> null // chapter transition, a run boundary
            } ?: break
            columns += prevPages.sumOf { if (isWide(it)) 2 else 1 }
        }
        return columns
    }

    /**
     * The pairing shift a chapter is pinned to by a wide two-page spread in it, or null if none pins it.
     * A facing spread is a complete pairing slot, so its column fixes the parity: the first interior wide
     * (position >= 1 in the chapter's page list) must land at an even column: its column is its index plus
     * the wides before it, so a wraparound cover (a wide at position 0) counts, shifting the pinned parity
     * by one. When several wides pin the same chapter they must agree; a disagreement is the
     * wide-regular-wide structural inconsistency (an odd number of single pages between two spreads), which
     * is out of the correctness domain, so the first interior wide decides. A lone cover with no interior
     * wide still pins: it occupies a column the parity crosses, so the run after it pairs cleanly only
     * unshifted; it returns false, not null, so a floating default (or detected) shift can't offset the
     * post-cover pages away from the feature-off pairing. Consumed by the shift resolver when "treat wide
     * pages as spreads" is on.
     */
    fun decisiveShiftFromWides(pages: List<ReaderPage>, isWide: (ReaderPage) -> Boolean): Boolean? {
        val coverWide = pages.isNotEmpty() && isWide(pages[0])
        for (i in 1 until pages.size) {
            if (isWide(pages[i])) return (i + if (coverWide) 1 else 0) % 2 == 1
        }
        return if (coverWide) false else null
    }

    /**
     * Rebuilds [source] (display order) into spreads run by run: a run ends at any [InsertPage],
     * chapter transition, or chapter boundary. Each run is paired by [pairInStoryOrder] with its chapter's
     * own [shiftFor] shift, so the current chapter and each neighbour preview carry their own parity. All
     * pairing is done in story order and converted back to display order only at the run boundaries, so R2L
     * odd-length chapters shift correctly.
     *
     * [PageSpread]s already present in [source] are unwrapped (in display order) and re-paired.
     */
    fun regroupChapterAware(
        source: List<Any>,
        isR2L: Boolean,
        shiftFor: (ReaderChapter) -> Boolean,
        isWide: (ReaderPage) -> Boolean = { false },
    ): MutableList<Any> {
        val newItems = mutableListOf<Any>()
        val buffer = mutableListOf<ReaderPage>()
        var bufferChapter: ReaderChapter? = null

        fun flushBuffer() {
            if (buffer.isEmpty()) return
            val storyOrder = if (isR2L) buffer.asReversed() else buffer
            val runChapter = bufferChapter
            val storyResult = pairInStoryOrder(storyOrder, shifted = runChapter != null && shiftFor(runChapter), isWide)
            newItems.addAll(if (isR2L) storyResult.asReversed() else storyResult)
            buffer.clear()
        }

        for (item in source) {
            val pagesInItem = when (item) {
                is InsertPage -> null // never paired; also a boundary between runs
                is ReaderPage -> listOf(item)
                is PageSpread -> spreadOrder(item, isR2L).toList()
                else -> null // chapter transition: a boundary
            }
            if (pagesInItem == null) {
                flushBuffer()
                newItems.add(item)
                continue
            }
            val chapterOfItem = pagesInItem.first().chapter
            if (buffer.isNotEmpty() && chapterOfItem != bufferChapter) flushBuffer()
            bufferChapter = chapterOfItem
            buffer.addAll(pagesInItem)
        }
        flushBuffer()
        return newItems
    }
}
