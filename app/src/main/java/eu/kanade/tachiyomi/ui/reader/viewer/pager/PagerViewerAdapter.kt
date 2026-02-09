package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderItemPair
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import tachiyomi.core.common.util.system.logcat

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    var items: MutableList<Any> = mutableListOf()
        private set
        
    private var lastSideBySideMode: Boolean = viewer.config.sideBySideMode

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext = viewer.activity.createReaderThemeContext()

    /**
     * Helper to group a list of items into pairs consistently.
     */
    private fun groupIntoPairs(items: List<Any>): List<ReaderItemPair> {
        val pairs = mutableListOf<ReaderItemPair>()
        var i = 0
        while (i < items.size) {
            val first = items[i]
            val second = items.getOrNull(i + 1)
            if (second != null && second !is ChapterTransition) {
                pairs.add(ReaderItemPair(first, second))
                i += 2
            } else {
                pairs.add(ReaderItemPair(first))
                i += 1
            }
        }
        return pairs
    }

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        // Update mode tracker
        lastSideBySideMode = viewer.config.sideBySideMode
        
        val newItems = mutableListOf<Any>()

        // Forces chapter transition if there is missing chapters
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Add previous chapter pages and transition.
        if (chapters.prevChapter != null) {
            // We only need to add the last few pages of the previous chapter, because it'll be
            // selected as the current chapter when one of those pages is selected.
            val prevPages = chapters.prevChapter.pages
            if (prevPages != null) {
                newItems.addAll(prevPages.takeLast(2))
            }
        }

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

        if (chapters.nextChapter != null) {
            // Add at most two pages, because this chapter will be selected before the user can
            // swap more pages.
            val nextPages = chapters.nextChapter.pages
            if (nextPages != null) {
                newItems.addAll(nextPages.take(2))
            }
        }

        // Resets double-page splits, else insert pages get misplaced
        items.filterIsInstance<InsertPage>().also { items.removeAll(it) }

        if (viewer is R2LPagerViewer) {
            newItems.reverse()
        }

        if (viewer.config.sideBySideMode) {
            val pairedItems = mutableListOf<Any>()
            
            // 1. Add Previous Chapter Buffer (Consistently paired)
            if (chapters.prevChapter != null) {
                val prevPages = chapters.prevChapter.pages
                if (prevPages != null) {
                    val prevPairs = groupIntoPairs(prevPages)
                    if (prevPairs.isNotEmpty()) {
                        pairedItems.add(prevPairs.last())
                    }
                }
            }

            // 2. Add Previous Transition (Conditional)
            val prevNotLoaded = chapters.prevChapter?.state !is ReaderChapter.State.Loaded
            if (prevHasMissingChapters || forceTransition || prevNotLoaded) {
                pairedItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
            }

            // 3. Add Current Chapter Pages grouped in pairs
            val currPagesList = chapters.currChapter.pages
            if (currPagesList != null) {
                val pages = currPagesList.toMutableList()
                preprocessed.keys.sortedDescending().forEach { key ->
                    preprocessed[key]?.let { pages.add(key + 1, it) }
                }
                pairedItems.addAll(groupIntoPairs(pages))
            }

            // 4. Add Next Transition (Conditional)
            val nextTrans = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            val nextNotLoaded = chapters.nextChapter?.state !is ReaderChapter.State.Loaded
            if (nextHasMissingChapters || forceTransition || nextNotLoaded) {
                pairedItems.add(nextTrans)
            }
            nextTransition = nextTrans

            // 5. Add Next Chapter Buffer
            if (chapters.nextChapter != null) {
                val nextPages = chapters.nextChapter.pages
                if (nextPages != null) {
                    val nextPairs = groupIntoPairs(nextPages)
                    if (nextPairs.isNotEmpty()) {
                        pairedItems.add(nextPairs.first())
                    }
                }
            }

            if (viewer is R2LPagerViewer) {
                pairedItems.reverse()
            }
            items = pairedItems
        } else {
            // Standard Single-Page Logic
            if (viewer is R2LPagerViewer) {
                newItems.reverse()
            }
            items = newItems
        }

        preprocessed = mutableMapOf()
        notifyDataSetChanged()

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage!!)
        }
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return items.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        return when (val item = items[position]) {
            is ReaderPage -> PagerPageHolder(readerThemedContext, viewer, item)
            is ReaderItemPair -> PagerPagePairHolder(readerThemedContext, viewer, item)
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        // If mode changed, force recreation of all views
        if (lastSideBySideMode != viewer.config.sideBySideMode) {
            return POSITION_NONE
        }
        
        if (view is PositionableView) {
            val item = view.item
            val position = items.indexOf(item)
            if (position != -1) {
                return position
            } else {
                // If it's a ReaderPage, it might be inside a ReaderItemPair
                if (item is ReaderPage) {
                    val pairPosition = items.indexOfFirst { it is ReaderItemPair && (it.first == item || it.second == item) }
                    if (pairPosition != -1) return pairPosition
                }
                logcat { "Position for $item not found" }
            }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        if (currentPage !is ReaderPage) return

        if (viewer.config.sideBySideMode) {
            // If already in side-by-side mode, we just need to add the new page to preprocessed
            // so it gets picked up on the next natural refresh or chapter load.
            preprocessed[newPage.index] = newPage
            
            // Trigger a refresh so the new page is paired correctly
            viewer.activity.runOnUiThread {
                viewer.refreshAdapter()
            }
            return
        }

        val currentIndex = items.indexOf(currentPage)

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
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && items[placeAtIndex - 1] is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (items[placeAtIndex] is InsertPage) {
            return
        }

        items.add(placeAtIndex, newPage)

        notifyDataSetChanged()
    }

    fun cleanupPageSplit() {
        val insertPages = items.filterIsInstance(InsertPage::class.java)
        items.removeAll(insertPages)
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
    }
}