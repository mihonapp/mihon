package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Loader used to load chapters from an online source.
 */
internal class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache,
    private val appScope: CoroutineScope,
    private val canLoadAhead: () -> Boolean,
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunkProcessLock = Any()
    private var chunkProcessJob: Job? = null
    private val downloadJobs = ConcurrentHashMap<Int, Job>()

    override var isLocal: Boolean = false

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     */
    override suspend fun getPages(): List<ReaderPage> {
        val pages = try {
            chapterCache.getPageListFromCache(chapter.chapter)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            source.getPageList(chapter.chapter.toSChapter())
        }
        return pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl).also {
                // Loading starts below, before ChapterLoader gets to set this, and chapter is a lateinit
                it.chapter = chapter
            }
        }
            .also {
                it.loadByRollingChunked(0, 1, 1)
            }
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    override suspend fun loadPage(page: ReaderPage) = withContext(Dispatchers.IO) {
        val imageUrl = page.imageUrl

        // Check if the image has been deleted
        if (page.status == Page.State.Ready && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
            page.status = Page.State.Queue
        }

        // Automatically retry failed pages when subscribed to this page
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }

        // Start the page being read before anything else. Planning the chunks walks the whole chapter and takes
        // chunkProcessLock, and the chain only reaches this page once it has, so don't make it wait on either.
        launchLoadOnce(page)

        val pages = page.chapter.pages.orEmpty()
        if (page.index !in pages.indices) return@withContext
        pages.loadByRollingChunked(page.index, 3, 5) { chunks ->
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
        scope.launch { internalLoadPage(page, force = true) }.trackAsDownloadJob(pageIndex)
    }

    override fun recycle() {
        super.recycle()
        scope.cancel()
        downloadJobs.clear()

        // Cache current page list progress for online chapters to allow a faster reopen
        chapter.pages?.let { pages ->
            appScope.launch(Dispatchers.IO) {
                try {
                    // Convert to pages without reader information
                    val pagesToSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(chapter.chapter, pagesToSave)
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    /**
     * Keeps [this] in [downloadJobs] under [pageIndex] until it completes. The entry is only removed if it still
     * points at this job, so a newer job for the same page isn't evicted by an older one finishing.
     */
    private fun Job.trackAsDownloadJob(pageIndex: Int) = also { job ->
        downloadJobs[pageIndex] = job
        job.invokeOnCompletion { downloadJobs.remove(pageIndex, job) }
    }

    /**
     * Starts loading [page] unless it's already loaded or a load is already in flight for it. Claiming the slot and
     * starting the job has to be atomic, otherwise two chunk chains can both launch the same page.
     */
    private fun launchLoadOnce(page: ReaderPage): Job? {
        val pageIndex = page.index
        // A page another chain is already loading still counts towards this chunk, otherwise the chain moves on
        // and starts the next chunk while it's in flight
        downloadJobs[pageIndex]?.takeIf { it.isActive }?.let { return it }
        if (page.status !is Page.State.Queue && page.status !is Page.State.Error) return null

        val job = scope.launch(start = CoroutineStart.LAZY) { internalLoadPage(page) }
        val running = downloadJobs.putIfAbsent(pageIndex, job)
        if (running != null) {
            job.cancel()
            // Hand back the in flight job so the chunk still waits for this page and the chunk size keeps bounding
            // how many downloads run at once
            return running
        }
        job.invokeOnCompletion { downloadJobs.remove(pageIndex, job) }
        job.start()
        return job
    }

    private fun List<ReaderPage>.loadByRollingChunked(
        index: Int,
        chunkStartSize: Int,
        chunkEndSize: Int,
        onNewChunks: (List<List<ReaderPage>>) -> Unit = {},
    ) {
        val items = this
        // When loading ahead isn't allowed, only the pages around the one being read load
        val window = if (canLoadAhead()) items.indices else (index - 1)..(index + LIMITED_PAGES_AHEAD)
        val chunks = buildList {
            items.getOrNull(index)?.let(::add)
            items.getOrNull(index - 1)?.let(::add)
            items.getOrNull(index + 1)?.let(::add)
            // `items.lastIndex`, not the list being built, whose lastIndex is at most 2 here
            if (index < items.lastIndex - 1) {
                items.subList(index + 2, items.size).let(::addAll)
            }
            if (index > 1) {
                items.subList(0, index - 1).reversed().let(::addAll)
            }
        }
            .filter { it.index in window }
            .rollingChunked(chunkStartSize, chunkEndSize)
            .also(onNewChunks)

        // Every visible page holder calls loadPage, so this runs concurrently
        synchronized(chunkProcessLock) {
            chunkProcessJob?.cancel()
            chunkProcessJob = scope.launch {
                for (chunk in chunks) {
                    val jobs = chunk.mapNotNull { page -> launchLoadOnce(page) }
                    jobs.joinAll()
                }
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
        // A start size of 0 never advances the index
        require(startSize >= 1) { "startSize must be at least 1, was $startSize" }
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
                val live = page.downloadStream
                page.status = Page.State.DownloadImage
                try {
                    val imageResponse = source.getImage(page)
                    chapterCache.putImageToCache(imageUrl, imageResponse, live)
                    live?.finish()
                } catch (e: Throwable) {
                    live?.finish(e)
                    throw e
                }
            }

            page.downloadStream = null
            page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            page.downloadStream = null
            if (e is CancellationException) {
                // Chunk chains get cancelled on every page turn, so a cancelled page isn't an error. Put it back in
                // the queue, otherwise it's stuck in a state no later pass will pick up.
                page.status = Page.State.Queue
                throw e
            }
            page.status = Page.State.Error(e)
        }
    }
}

private const val LIMITED_PAGES_AHEAD = 4
