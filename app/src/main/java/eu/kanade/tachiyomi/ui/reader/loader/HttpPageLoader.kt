package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.core.util.indexOfFirstOrNull
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Loader used to load chapters from an online source.
 */
internal class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache = Injekt.get(),
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var chunkProcessJob: Job? = null
    private val downloadJobs = ConcurrentHashMap<Int, Job>()

    override var isLocal: Boolean = false

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     */
    override suspend fun getPages(): List<ReaderPage> {
        val pages = try {
            chapterCache.getPageListFromCache(chapter.chapter.toDomainChapter()!!)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            source.getPageList(chapter.chapter)
        }
        return pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl)
        }
            .also {
                it.loadByRollingChunked(0, 1, 1)
            }
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        val imageUrl = page.imageUrl

        // Check if the image has been deleted
        if (page.status == Page.State.Ready && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
            page.status = Page.State.Queue
        }

        // Automatically retry failed pages when subscribed to this page
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }

        val pages = page.chapter.pages.orEmpty()
        val currentIndex = pages.indexOfFirstOrNull { it.index == page.index } ?: return@withIOContext
        pages.loadByRollingChunked(currentIndex, 3, 5) { chunks ->
            val continueLoading = chunks.take(2).flatten().map { it.index }.toSet()
            downloadJobs.keys.filter { it !in continueLoading }
                .forEach { downloadJobs.remove(it)?.cancel() }
        }
    }

    /**
     * Retries a page. This method is only called from user interaction on the viewer.
     */
    override fun retryPage(page: ReaderPage) {
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }

        val pageIndex = page.index
        downloadJobs.remove(pageIndex)?.cancel()
        val retryJob = scope.launch { internalLoadPage(page, force = true) }
        downloadJobs[pageIndex] = retryJob
        retryJob.invokeOnCompletion { downloadJobs.remove(pageIndex) }
    }

    override fun recycle() {
        super.recycle()
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()

        // Cache current page list progress for online chapters to allow a faster reopen
        chapter.pages?.let { pages ->
            scope.launch {
                try {
                    // Convert to pages without reader information
                    val pagesToSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(chapter.chapter.toDomainChapter()!!, pagesToSave)
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
                .invokeOnCompletion {
                    scope.cancel()
                }
        }
    }

    private fun List<ReaderPage>.loadByRollingChunked(
        index: Int,
        chunkStartSize: Int,
        chunkEndSize: Int,
        onNewChunks: (List<List<ReaderPage>>) -> Unit = {
        },
    ) {
        val items = this
        val chunks = buildList {
            items.getOrNull(index)?.let(::add)
            items.getOrNull(index - 1)?.let(::add)
            items.getOrNull(index + 1)?.let(::add)
            if (index < lastIndex - 1) {
                items.subList(index + 2, items.size).let(::addAll)
            }
            if (index > 1) {
                items.subList(0, index - 1).reversed().let(::addAll)
            }
        }
            .rollingChunked(chunkStartSize, chunkEndSize)
            .also(onNewChunks)

        chunkProcessJob?.cancel()
        chunkProcessJob = scope.launch {
            for (chunk in chunks) {
                val jobs = chunk.mapNotNull { page ->
                    val pageIndex = page.index
                    if (downloadJobs.containsKey(pageIndex) ||
                        page.status is Page.State.DownloadImage
                    ) {
                        return@mapNotNull null
                    }
                    scope
                        .launch { internalLoadPage(page) }
                        .also { job ->
                            downloadJobs[pageIndex] = job
                            job.invokeOnCompletion {
                                downloadJobs.remove(pageIndex)
                            }
                        }
                }
                jobs.joinAll()
            }
        }
    }

    /**
     * Splits the list into chunks with rolling sizes between a starting size and an ending size.
     *
     * The chunking process starts with the given `startSize` and increases by 1 until it reaches
     * the `endSize`, at which point it continues with the `endSize` size for the remaining items.
     */
    private fun <T> List<T>.rollingChunked(startSize: Int, endSize: Int): List<List<T>> {
        val thisSize = this.size
        val result = ArrayList<List<T>>()
        var chunkSize = startSize
        var index = 0
        while (index < thisSize) {
            val localChunkSize = chunkSize.coerceAtMost(thisSize - index)
            result.add(List(localChunkSize) { this[it + index] })
            index += chunkSize
            if (chunkSize < endSize) chunkSize += 1
        }
        return result
    }

    /**
     * Loads the page, retrieving the image URL and downloading the image if necessary.
     * Downloaded images are stored in the chapter cache.
     *
     * @param page the page whose source image has to be downloaded.
     */
    private suspend fun internalLoadPage(page: ReaderPage, force: Boolean = false) {
        try {
            if (page.imageUrl.isNullOrEmpty()) {
                page.status = Page.State.LoadPage
                page.imageUrl = source.getImageUrl(page)
            }
            val imageUrl = page.imageUrl!!

            if (force || !chapterCache.isImageInCache(imageUrl)) {
                page.status = Page.State.DownloadImage
                val imageResponse = source.getImage(page)
                chapterCache.putImageToCache(imageUrl, imageResponse)
            }

            page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            page.status = Page.State.Error(e)
            if (e is CancellationException) {
                throw e
            }
        }
    }
}
