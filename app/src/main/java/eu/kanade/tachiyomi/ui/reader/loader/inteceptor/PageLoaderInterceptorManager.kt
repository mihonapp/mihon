package eu.kanade.tachiyomi.ui.reader.loader.inteceptor

import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import java.util.WeakHashMap

class PageLoaderInterceptorManager(private val interceptors: List<(List<ReaderPage>) -> PageLoaderInterceptor>) {
    private val pageCollectorFlows = WeakHashMap<ReaderPage, List<Flow<Any>>>()
    private val chapterCache = WeakHashMap<ReaderChapter, List<ReaderPage>>()

    /**
     * Get a [ReaderPage] whose state reflects [page] after interceptors are applied.
     */
    fun getInterceptedPage(page: ReaderPage): ReaderPage {
        val chapterPages = chapterCache.getOrPut(page.chapter) {
            val originalPages = page.chapter.pages ?: return page

            val collectorFlows = originalPages.map { mutableListOf<Flow<Any>>() }
            interceptors.fold(originalPages) { pages, createInterceptor ->
                val proxyPages = pages.map { ProxyPage(originalPages[it.index]) }
                val interceptor = createInterceptor(proxyPages)
                pages.forEach { page ->
                    // These flows will be launched when the page is loaded under the job
                    // in which the page is loaded.
                    collectorFlows[page.index].add(
                        page.statusFlow.onEach {
                            interceptor.onStatus(page)
                        },
                    )
                    collectorFlows[page.index].add(
                        page.progressFlow.onEach {
                            interceptor.onProgress(page)
                        },
                    )
                }
                proxyPages
            }.also {
                pageCollectorFlows.putAll(collectorFlows.mapIndexed { i, flows -> originalPages[i] to flows })
            }
        }
        return chapterPages[page.index]
    }

    /**
     * Gets the original page from an intercepted page. If [page] is not intercepted, it will be returned back as is.
     * This method is primarily used when the original page is required for referential equality,
     */
    fun getOriginalPage(page: ReaderPage): ReaderPage {
        return if (page is ProxyPage) page.originalPage else page
    }

    /**
     * Loads [page] while ensuring that interceptors are properly applied. This should only be called on intercepted
     * pages.
     * @param page a page obtained from calling [getInterceptedPage]
     * @see [PageLoader.loadPage]
     */
    suspend fun loadPage(page: ReaderPage) {
        val pageLoader = page.chapter.pageLoader ?: return
        if (page is ProxyPage) {
            val originalPage = page.originalPage
            // Not supervisor scope because if anything fails here, nothing will work.
            coroutineScope {
                // loadPage is allowed to never continue, so it needs to be launched
                launch { pageLoader.loadPage(originalPage) }
                pageCollectorFlows[originalPage]?.let { flows ->
                    // These jobs will not need to be cancelled manually, since their lifetime should equal
                    // the lifetime of the current job.
                    // We don't want to remove the flows from pageCollectorFlows because if this job gets cancelled
                    // and the page is re-loaded, we need to be able to launch the flows again in the new scope.
                    flows.forEach { it.launchIn(this) }
                }
            }
        } else {
            pageLoader.loadPage(page)
        }
    }

    fun retryPage(page: ReaderPage) {
        page.chapter.pageLoader?.retryPage(if (page is ProxyPage) page.originalPage else page)
    }

    private class ProxyPage(val originalPage: ReaderPage) :
        ReaderPage(originalPage.index, originalPage.url, originalPage.imageUrl) {
        init {
            chapter = originalPage.chapter
        }
    }
}
