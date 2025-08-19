package eu.kanade.tachiyomi.ui.reader.loader.inteceptor

import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.github.theunlocked.mangavision.SpreadDetector
import kotlinx.coroutines.sync.Mutex
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import java.lang.ref.WeakReference
import java.util.WeakHashMap

class SpreadFusionInterceptor(pages: List<ReaderPage>, private val isLTR: Boolean) : PageLoaderInterceptor(pages) {
    private val checked = mutableSetOf<ReaderPage>()
    private val partOfSpread = mutableSetOf<ReaderPage>()

    override suspend fun onStatus(interceptedPage: ReaderPage) {
        // This logic is non-deterministic if three or more pages in a row all form an extra-long spread, but that
        // scenario is rare and probably not worth accounting for here.
        // The `partOfSpread` set helps ensure that even if merging is non-deterministic, all pages will be
        // displayed exactly once, even in the case of 3+ page-long spreads.

        val interceptedPages = interceptedPage.chapter.pages ?: return super.onStatus(interceptedPage)

        val prevPage = interceptedPages.getOrNull(interceptedPage.index - 1)
        val nextPage = interceptedPages.getOrNull(interceptedPage.index + 1)

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

                if (nextPage != null && nextPage.status == Page.State.Ready && !checked.contains(interceptedPage)) {
                    if (trySpread(interceptedPage, nextPage)) {
                        return
                    }
                }

                if (prevPage != null && prevPage.status == Page.State.Ready && !checked.contains(prevPage)) {
                    if (trySpread(prevPage, interceptedPage)) {
                        return
                    }
                }
            }
        }

        super.onStatus(interceptedPage)
    }

    private fun trySpread(
        page1: ReaderPage,
        page2: ReaderPage,
    ): Boolean {
        checked.add(page1)

        val leftPage: ReaderPage
        val rightPage: ReaderPage
        if (isLTR) {
            leftPage = page1
            rightPage = page2
        }
        else {
            leftPage = page2
            rightPage = page1
        }

        val leftBitmapFn = leftPage.bitmap ?: return false
        val rightBitmapFn = rightPage.bitmap ?: return false

        if (!SpreadDetector.isSpread(
            getOrCreateWeaklyCachedBitmap(leftPage, leftBitmapFn),
            getOrCreateWeaklyCachedBitmap(rightPage, rightBitmapFn),
        )) {
            return false
        }

        page1.output.apply {
            bitmap = { ImageUtil.mergeHorizontal(
                getOrCreateWeaklyCachedBitmap(leftPage, leftBitmapFn),
                getOrCreateWeaklyCachedBitmap(rightPage, rightBitmapFn),
            ) }
            if (status == Page.State.Ready) {
                refreshStatus()
            }
            else {
                status = Page.State.Ready
            }
        }
        page2.output.apply {
            status = Page.State.Skip
        }
        partOfSpread.add(page1)
        partOfSpread.add(page2)
        logcat { "Detected spread on pages ${page1.index}-${page2.index}" }
        return true
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

    private val weakBitmapCache = WeakHashMap<ReaderPage, Pair<Int, WeakReference<Bitmap>>>()

    /**
     * Gets the bitmap from [bitmapFn], weakly cached using [page] and its
     * [imageHashCode][ReaderPage.imageHashCode] as the key. Both the key and value references are weak.
     */
    private fun getOrCreateWeaklyCachedBitmap(page: ReaderPage, bitmapFn: () -> Bitmap): Bitmap {
        // Using weak references to cache data like this is generally not great practice, but the alternative is either
        // keeping potentially large bitmaps in memory for an extended period of time (not good), or just not caching
        // and instead re-generating the bitmaps every time (simpler, but slower). Even with the extremely short
        // lifespan of cache entries, this can reduce the number of calls to bitmapFn by half or more.
        return weakBitmapCache[page]?.let {
            if (it.first != page.imageHashCode()) {
                null
            }
            else {
                it.second.get()
            }
        } ?: bitmapFn().let {
            weakBitmapCache[page] = page.imageHashCode() to WeakReference(it)
            it
        }
    }
}
