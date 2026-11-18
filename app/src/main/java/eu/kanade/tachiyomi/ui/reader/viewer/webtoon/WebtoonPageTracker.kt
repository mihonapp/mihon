package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.core.common.util.system.logcat
import kotlin.math.abs

/**
 * The item a webtoon viewer of [items] is on, shared by the view and Compose viewers. It tells [activity] of each new
 * page and preloads the chapters around it. A scroll further than [hideThreshold] hides the menu.
 */
class WebtoonPageTracker(
    private val activity: ReaderActivity,
    private val items: WebtoonItems,
    private val hideThreshold: Int,
) {

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    var currentPage: Any? = null
        private set

    /**
     * Marks the item at [position], the last one whose end is visible, as active.
     */
    fun onScrolled(position: Int) {
        val item = items.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Called when the list scrolled by [dy] pixels, leaving the items from [firstVisible] to [lastEndVisible] on
     * screen.
     */
    fun onScrolledBy(dy: Float, firstVisible: Int, lastEndVisible: Int) {
        if (abs(dy) > hideThreshold && activity.viewModel.state.value.menuVisible) {
            activity.hideMenu()
        }

        if (dy < 0) {
            val firstItem = items.items.getOrNull(firstVisible)
            if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                activity.requestPreloadChapter(firstItem.to)
            }
        }

        val lastItem = items.items.getOrNull(lastEndVisible)
        if (lastItem is ChapterTransition.Next && lastItem.to == null) {
            activity.showMenu()
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        // Allow preload for
        // 1. Going between pages of same chapter
        // 2. Next chapter page
        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            lastChapter() -> true
            else -> false
        }
    }

    /**
     * Called when a [page] is marked as active. It notifies the activity of the change and requests the preload of
     * the next chapter if this is the last page.
     */
    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == items.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            val transitionChapter = lastChapter()
            if (transitionChapter != null) {
                logcat { "Requesting to preload chapter ${transitionChapter.chapter.chapterNumber}" }
                activity.requestPreloadChapter(transitionChapter)
            }
        }
    }

    /**
     * Called when a [transition] is marked as active. It request the preload of the destination chapter of the
     * transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        }
    }

    /**
     * The chapter at the end of the list: the next transition's, or that of the next chapter's pages.
     */
    private fun lastChapter() = when (val lastItem = items.items.lastOrNull()) {
        is ChapterTransition.Next -> lastItem.to
        is ReaderPage -> lastItem.chapter
        else -> null
    }
}
