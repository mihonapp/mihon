package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.ui.reader.model.DualPage
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
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
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

        val itemsToSet = if (viewer.config.dualPageMode) {
            groupPages(newItems)
        } else {
            newItems
        }

        if (viewer is R2LPagerViewer) {
            itemsToSet.reverse()
        }

        logcat { "setChapters: chapter=${chapters.currChapter.chapter.url}, pages=${chapters.currChapter.pages?.size}, dualMode=${viewer.config.dualPageMode}" }
        preprocessed = mutableMapOf()
        items = itemsToSet
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
            is DualPage -> PagerDualPageHolder(readerThemedContext, viewer, item)
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = items.indexOf(view.item)
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

        val currentIndex = items.indexOfFirst {
            it === currentPage || (it is DualPage && (it.first === currentPage || it.second === currentPage))
        }
        if (currentIndex == -1) return

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
        if (items.getOrNull(placeAtIndex) is InsertPage) {
            return
        }

        items.add(placeAtIndex, newPage)

        if (viewer.config.dualPageMode) {
            regroup()
        } else {
            notifyDataSetChanged()
        }
    }

    fun cleanupPageSplit() {
        // This is complex because InsertPage might be part of a DualPage.
        // Easier to flatten, filter, and regroup if needed.
        val rawItems = if (viewer.config.dualPageMode) {
            items.flatMap { if (it is DualPage) listOfNotNull(it.first, it.second) else listOf(it) }
        } else {
            items
        }.filterNot { it is InsertPage }
        
        items = if (viewer.config.dualPageMode) groupPages(rawItems) else rawItems.toMutableList()
        notifyDataSetChanged()
    }

    private fun groupPages(rawItems: List<Any>): MutableList<Any> {
        val grouped = mutableListOf<Any>()
        var i = 0
        while (i < rawItems.size) {
            val item1 = rawItems[i]
            if (item1 is ReaderPage) {
                // Check if this page should be a cover (single page spread)
                val isCover = item1.index == 0 && viewer.config.dualPageFirstPageCover
                
                if (isCover) {
                    grouped.add(DualPage(item1))
                    i += 1
                } else {
                    val item2 = rawItems.getOrNull(i + 1)
                    if (item2 is ReaderPage && item2.chapter == item1.chapter) {
                        grouped.add(DualPage(item1, item2))
                        i += 2
                    } else {
                        grouped.add(DualPage(item1))
                        i += 1
                    }
                }
            } else {
                grouped.add(item1)
                i += 1
            }
        }
        logcat { "groupPages: rawItems=${rawItems.size} -> grouped=${grouped.size}" }
        return grouped
    }

    private fun regroup() {
        val rawItems = items.flatMap {
            if (it is DualPage) listOfNotNull(it.first, it.second) else listOf(it)
        }.toMutableList()
        if (viewer is R2LPagerViewer) {
            rawItems.reverse()
        }
        val grouped = groupPages(rawItems)
        if (viewer is R2LPagerViewer) {
            grouped.reverse()
        }
        items = grouped
        notifyDataSetChanged()
    }

    private fun flatten() {
        val rawItems = items.flatMap {
            if (it is DualPage) listOfNotNull(it.first, it.second) else listOf(it)
        }.toMutableList()
        // items are already in visual order. 
        // If we flatten a reversed list G2[4,3], G1[2,1], we get [4,3,2,1] which is correct for RTL.
        items = rawItems
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
        val isCurrentlyGrouped = items.any { it is DualPage }
        if (viewer.config.dualPageMode && !isCurrentlyGrouped) {
            regroup()
        } else if (!viewer.config.dualPageMode && isCurrentlyGrouped) {
            flatten()
        }
    }
}
