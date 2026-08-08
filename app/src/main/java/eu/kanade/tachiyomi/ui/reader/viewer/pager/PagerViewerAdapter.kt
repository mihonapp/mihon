package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderItem
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.delay
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.system.logcat

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted. In double-page
 * mode it pairs consecutive same-chapter pages into spreads via [joinedItems] / [setJoinedItems].
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * Paired list of currently set items. Each entry is a single pager slot holding one page (and,
     * in double-page mode, an optional second page) or a chapter transition.
     */
    var joinedItems: MutableList<Pair<ReaderItem, ReaderItem?>> = mutableListOf()
        private set

    /**
     * Flat list of items before they are joined into pairs.
     */
    private var subItems: MutableList<ReaderItem> = mutableListOf()

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null

    /** Variables used to check if config of the pages have changed */
    private var shifted = viewer.config.shiftDoublePage
    private var doubledUp = viewer.config.doublePages

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext = viewer.activity.createReaderThemeContext()

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<ReaderItem>()

        // Forces chapter transition if there is missing chapters
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Add previous chapter pages and transition
        chapters.prevChapter?.pages?.let(newItems::addAll)

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        var insertPageLastPage: InsertPage? = null

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            val pages = currPages.toMutableList()

            val lastPage = pages.last()

            // Insert preprocessed pages into current page list
            preprocessed.keys.sortedDescending()
                .forEach { key ->
                    if (lastPage.index == key) {
                        insertPageLastPage = preprocessed[key]
                    }
                    preprocessed[key]?.let { pages.add(key + 1, it) }
                }

            newItems.addAll(pages)
        }

        currentChapter = chapters.currChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (
                    nextHasMissingChapters ||
                    forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        chapters.nextChapter?.pages?.let(newItems::addAll)

        // Resets double-page splits, else insert pages get misplaced
        subItems.filterIsInstance<InsertPage>().also { subItems.removeAll(it) }

        preprocessed = mutableMapOf()
        subItems = newItems.toMutableList()

        var useSecondPage = false
        if (shifted != viewer.config.shiftDoublePage || (doubledUp != viewer.config.doublePages && doubledUp)) {
            if (shifted && (doubledUp == viewer.config.doublePages)) {
                useSecondPage = true
            }
            shifted = viewer.config.shiftDoublePage
        }
        doubledUp = viewer.config.doublePages
        setJoinedItems(useSecondPage)

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage)
        }
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return joinedItems.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        val item = joinedItems[position].first
        val item2 = joinedItems[position].second
        return when (item) {
            is ReaderPage -> PagerPageHolder(readerThemedContext, viewer, item, item2 as? ReaderPage)
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val item = view.item
            // Page holders expose the full (page, extraPage) pair — require an exact pair match so a
            // holder whose pairing changed on re-layout is recreated instead of left showing a stale
            // spread. Transition holders expose the bare ChapterTransition — match it as a slot's first.
            val position = if (item is Pair<*, *>) {
                joinedItems.indexOf(item)
            } else {
                joinedItems.indexOfFirst { it.first == item }
            }
            if (position != -1) {
                return position
            } else {
                logcat { "Position for ${view.item} not found" }
            }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        if (currentPage !is ReaderPage) return

        val currentIndex = joinedItems.indexOfFirst { it.first == currentPage }

        // Put aside preprocessed pages for next chapter so they don't get removed when changing chapter
        if (currentPage.chapter.chapter.id != currentChapter?.chapter?.id) {
            preprocessed[newPage.index] = newPage
            return
        }

        val placeAtIndex = when (viewer) {
            is L2RPagerViewer,
            is VerticalPagerViewer,
            -> currentIndex + 1
            else -> currentIndex
        }

        // It will enter a endless cycle of insert pages
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && joinedItems[placeAtIndex - 1].first is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (joinedItems[placeAtIndex].first is InsertPage) {
            return
        }

        joinedItems.add(placeAtIndex, newPage to null)

        notifyDataSetChanged()
    }

    fun cleanupPageSplit() {
        val insertPages = joinedItems.filter { it.first is InsertPage }
        joinedItems.removeAll(insertPages)
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
    }

    private fun setJoinedItems(useSecondPage: Boolean = false) {
        val oldCurrent = joinedItems.getOrNull(viewer.pager.currentItem)
        if (!viewer.config.doublePages) {
            // If not in double mode, set up items like before
            subItems.forEach { readerItem ->
                if (readerItem is ReaderPage) {
                    readerItem.shiftedPage = false
                }
            }
            this.joinedItems = subItems.map { Pair(it, null) }.toMutableList()
            if (viewer is R2LPagerViewer) {
                joinedItems.reverse()
            }
        } else {
            val pagedItems = mutableListOf<MutableList<ReaderPage?>>()
            // The transition (if any) that trails each segment, kept index-aligned with pagedItems so a
            // chapter boundary that emits no transition can't shift the association and mis-place it.
            val trailingTransitions = mutableListOf<ChapterTransition?>()
            pagedItems.add(mutableListOf())
            trailingTransitions.add(null)

            // Step 1: segment the pages and transition pages
            subItems.forEach { readerItem ->
                when (readerItem) {
                    is ReaderPage -> {
                        if (pagedItems.last().isNotEmpty() &&
                            pagedItems.last().last()?.chapter?.chapter?.id != readerItem.chapter.chapter.id
                        ) {
                            pagedItems.add(mutableListOf())
                            trailingTransitions.add(null)
                        }
                        pagedItems.last().add(readerItem)
                    }
                    is ChapterTransition -> {
                        // Attach this transition to the segment it follows, then open a new segment.
                        trailingTransitions[trailingTransitions.lastIndex] = readerItem
                        pagedItems.add(mutableListOf())
                        trailingTransitions.add(null)
                    }
                }
            }

            val subJoinedItems = mutableListOf<Pair<ReaderItem, ReaderItem?>>()

            // Step 2: pair each chapter segment into spread slots, then append its trailing transition.
            pagedItems.forEachIndexed { segmentIndex, items ->
                val pages = items.filterNotNull()
                val layout = DoublePagePairer.pair(pages.map { it.fullPage }, viewer.config.shiftDoublePage)
                pages.forEachIndexed { index, page ->
                    page.shiftedPage = index in layout.shiftedIndices
                    page.isolatedPage = index in layout.isolatedIndices
                }
                layout.slots.forEach { slot ->
                    subJoinedItems.add(Pair(pages[slot.first], slot.second?.let { pages[it] }))
                }

                trailingTransitions[segmentIndex]?.let {
                    subJoinedItems.add(Pair(it, null))
                }
            }

            if (viewer is R2LPagerViewer) {
                subJoinedItems.reverse()
            }

            this.joinedItems = subJoinedItems
        }
        notifyDataSetChanged()

        // Step 6: Move back to our previous page or transition page
        // The listener is likely off around now, but either way when shifting or doubling,
        // we need to set the page back correctly
        // We will however shift to the first page of the new chapter if the last page we were are
        // on is not in the new chapter that has loaded
        val newPage = when {
            oldCurrent?.first is ReaderPage &&
                (oldCurrent.first as ReaderPage).chapter != currentChapter &&
                (oldCurrent.second as? ChapterTransition)?.from != currentChapter ->
                subItems.find { it is ReaderPage && it.chapter == currentChapter }
            useSecondPage -> oldCurrent?.second ?: oldCurrent?.first
            else -> oldCurrent?.first ?: return
        }

        val index = when {
            newPage is ChapterTransition && joinedItems.none { it.first == newPage || it.second == newPage } -> {
                val filteredPages = joinedItems.filter {
                    it.first is ReaderPage &&
                        (it.first as ReaderPage).chapter == newPage.to
                }
                val page = if (newPage is ChapterTransition.Next) {
                    filteredPages.minByOrNull { (it.first as ReaderPage).index }?.first
                } else {
                    filteredPages.maxByOrNull { (it.first as ReaderPage).index }?.first
                }
                joinedItems.indexOfFirst { it.first == page || it.second == page }
            }
            else -> joinedItems.indexOfFirst { it.first == newPage || it.second == newPage }
        }

        viewer.pager.setCurrentItem(index, false)
    }

    fun splitDoublePages(current: ReaderPage) {
        val oldCurrent = joinedItems.getOrNull(viewer.pager.currentItem)
        val oldSecondPage = oldCurrent?.second as? ReaderPage
        val oldFirstPage = oldCurrent?.first as? ReaderPage
        val oldPage = oldSecondPage ?: oldFirstPage

        setJoinedItems(oldSecondPage == current || (current.index + 1) < (oldPage?.index ?: 0))

        // The listener may be removed when we split a page, so the ui may not have updated properly
        // This case usually happens when we load a new chapter and the first 2 pages need to split
        viewer.scope.launchUI {
            delay(100)
            viewer.onPageChange(viewer.pager.currentItem)
        }
    }
}
