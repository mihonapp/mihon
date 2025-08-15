package eu.kanade.tachiyomi.ui.reader.loader.inteceptor

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.github.theunlocked.mangavision.SpreadDetector
import kotlinx.coroutines.sync.Mutex
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat

class SpreadFusionInterceptor(pages: List<ReaderPage>, private val isLTR: Boolean) : PageLoaderInterceptor(pages) {
    private val checked = mutableSetOf<ReaderPage>()
    private val partOfSpread = mutableSetOf<ReaderPage>()
    private val pendingNeighbors = mutableMapOf<ReaderPage, MutableSet<ReaderPage>>()

    override suspend fun onStatus(interceptedPage: ReaderPage) {
        // Being in pendingNeighbors implies that the page is ready, so if a page is no longer ready, it should be
        // removed from the map. If it becomes ready again later, it can be re-added as part of the regular logic.
        pendingNeighbors.remove(interceptedPage)

        // This logic is non-deterministic if three or more pages in a row all form an extra-long spread, but that
        // scenario is rare and probably not worth accounting for here.
        // The `partOfSpread` set helps ensure that even if merging is non-deterministic, all pages will be
        // displayed exactly once, even in the case of 3+ page-long spreads.

        val interceptedPages = interceptedPage.chapter.pages ?: return super.onStatus(interceptedPage)

        val prevPage = interceptedPages.getOrNull(interceptedPage.index - 1)
        val nextPage = interceptedPages.getOrNull(interceptedPage.index + 1)

        val loadingAdjacentPages = mutableSetOf<ReaderPage>()

        if (interceptedPage.status == Page.State.Ready) {
            // `checked` and `partOfSpread` are used to avoid doing duplicate work, but they could also lead to a race
            // condition where one thread starts checking if a page is a spread, a second thread sees that the page is
            // already checked and assumes it's not a spread, the first thread determines it is a spread and writes the
            // correct value, and then the second thread overwrites that result with the (incorrect) passthrough value.
            // By locking the pages during spread detection, we avoid this race.
            lockPages(listOfNotNull(prevPage, interceptedPage, nextPage)) {
                if (partOfSpread.contains(interceptedPage)) {
                    return
                }

                if (nextPage != null && !checked.contains(interceptedPage)) {
                    if (nextPage.status.isLoading()) {
                        loadingAdjacentPages.add(nextPage)
                    }
                    else if (nextPage.status == Page.State.Ready && trySpread(interceptedPage, nextPage)) {
                        pendingNeighbors.remove(nextPage)
                        prevPage?.notifyNeighborIsReady(interceptedPage)
                        return
                    }
                }

                if (prevPage != null && !checked.contains(prevPage)) {
                    if (prevPage.status.isLoading()) {
                        loadingAdjacentPages.add(prevPage)
                    }
                    else if (prevPage.status == Page.State.Ready && trySpread(prevPage, interceptedPage)) {
                        pendingNeighbors.remove(prevPage)
                        nextPage?.notifyNeighborIsReady(interceptedPage)
                        return
                    }
                }
            }
        }

        // If we're in any terminal state we'll let the surrounding pages load. Technically this is only valid if the
        // current page is in the ready or skip state since if it's in an error state then on reload it could still
        // create a spread, but in practice, it's better to load half a spread than leave the adjacent pages pending.
        // If one of the pages gets changed to skip afterwards, it will just look a bit weird in the UI.
        if (!interceptedPage.status.isLoading()) {
            prevPage?.notifyNeighborIsReady(interceptedPage)
            nextPage?.notifyNeighborIsReady(interceptedPage)
        }

        if (loadingAdjacentPages.size > 0) {
            // Leave the page in a non-ready state because when a neighbor loads it could make this page part of a
            // spread. We want to track this relationship so when the neighbor loads it can update this page.
            logcat { "${interceptedPage.index} is waiting on ${loadingAdjacentPages.joinToString { it.index.toString() }}" }
            pendingNeighbors[interceptedPage] = loadingAdjacentPages
            return
        }

        super.onStatus(interceptedPage)
    }

    private fun trySpread(page1: ReaderPage, page2: ReaderPage): Boolean {
        checked.add(page1)

        var bitmapFn1 = page1.bitmap ?: return false
        var bitmapFn2 = page2.bitmap ?: return false

        if (!isLTR) {
            val tmp = bitmapFn1
            bitmapFn1 = bitmapFn2
            bitmapFn2 = tmp
        }

        val bitmap1 = bitmapFn1()
        val bitmap2 = bitmapFn2()

        try {
            if (!SpreadDetector.isSpread(bitmap1, bitmap2)) {
                return false
            }
        }
        finally {
            // We don't want to hold these in memory. When it comes time to render the page, the bitmaps can be
            // re-obtained for merging. In theory we could hold onto these for just the first time it loads since the
            // image will generally be fetched immediately after the state changes to ready, but that adds a lot of
            // complexity and it's probably not that big of a deal.
            bitmap1.recycle()
            bitmap2.recycle()
        }

        page1.output.apply {
            bitmap = { ImageUtil.mergeHorizontal(bitmapFn1(), bitmapFn2()) }
            status = Page.State.Ready
        }
        page2.output.apply {
            status = Page.State.Skip
        }
        partOfSpread.add(page1)
        partOfSpread.add(page2)
        logcat { "Detected spread on pages ${page1.index}-${page2.index}" }
        return true
    }

    private suspend fun ReaderPage.notifyNeighborIsReady(neighbor: ReaderPage) {
        logcat { "${this.index} was notified ${neighbor.index} is ready" }
        val waitingOn = pendingNeighbors[this] ?: return
        if (waitingOn.remove(neighbor) && waitingOn.size == 0) {
            pendingNeighbors.remove(this)
            super.onStatus(this)
        }
    }

    private fun Page.State.isLoading(): Boolean {
        return when(this) {
            Page.State.Queue -> true
            Page.State.LoadPage -> true
            Page.State.DownloadImage -> true
            Page.State.Ready -> false
            Page.State.Skip -> false
            is Page.State.Error -> false
        }
    }

    private val pageLocks = pages.map { Mutex() }

    private suspend inline fun lockPages(pages: List<ReaderPage>, block: () -> Unit) {
        // As long as locks are always acquired in the same order, deadlocks will not occur.
        val locks = pages.sortedBy { it.index }.map { pageLocks[it.index] }
        locks.forEach { it.lock() }
        try {
            block()
        }
        finally {
            locks.forEach { it.unlock() }
        }
    }
}
