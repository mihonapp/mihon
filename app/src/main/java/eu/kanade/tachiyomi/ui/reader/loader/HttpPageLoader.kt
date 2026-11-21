package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.chapter.model.toSChapter
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.data.cache.ChapterCache
import java.io.FileNotFoundException
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

    @Volatile
    private var selectedPage: ReaderPage? = null

    // Waiting loadPage calls by page index. Under chunkProcessLock.
    private val shownPages = HashMap<Int, Int>()

    override var isLocal: Boolean = false

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     */
    override suspend fun getPages(): List<ReaderPage> {
        val pages = chapterCache.getPageList(chapter.chapter)
            ?: source.getPageList(chapter.chapter.toSChapter())
        return pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl).also {
                // Loading starts below, before ChapterLoader gets to set this, and chapter is a lateinit
                it.chapter = chapter
            }
        }
            .also {
                it.loadByRollingChunked(minOf(chapter.requestedPage, it.lastIndex), 1, 1)
            }
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    override suspend fun loadPage(page: ReaderPage) {
        withContext(Dispatchers.IO) {
            requeueIfNeeded(page)
            synchronized(chunkProcessLock) {
                shownPages.merge(page.index, 1, Int::plus)
                loadAroundCurrent(page)
            }
            // Until cancelled: the page keeps its priority while the viewer shows it
            try {
                awaitCancellation()
            } finally {
                synchronized(chunkProcessLock) {
                    shownPages.computeIfPresent(page.index) { _, count -> (count - 1).takeIf { it > 0 } }
                }
            }
        }
    }

    fun onPageSelected(page: ReaderPage) {
        selectedPage = page
        scope.launch {
            requeueIfNeeded(page)
            loadAroundCurrent(page)
        }
    }

    private suspend fun requeueIfNeeded(page: ReaderPage) {
        val imageUrl = page.imageUrl

        // Check if the image has been deleted
        if (page.status == Page.State.Ready && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
            page.status = Page.State.Queue
        }

        // Automatically retry failed pages when subscribed to this page
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }
    }

    private fun loadAroundCurrent(requested: ReaderPage) = synchronized(chunkProcessLock) {
        // Under the lock, or a stale call cancels a newly selected page. Viewers also request its neighbours.
        val current = selectedPage ?: chapter.pages?.getOrNull(chapter.requestedPage) ?: requested
        val pages = chapter.pages.orEmpty()
        if (current.index !in pages.indices) return
        if (current.status != Page.State.Ready && current.status !is Page.State.Error) {
            // Not removed: launchLoadOnce waits for them to end
            downloadJobs
                .filter { (index, _) -> index != current.index && pages.getOrNull(index)?.isNearlyDownloaded != true }
                .values.forEach { it.cancel() }
        }

        launchLoadOnce(current)
        pages.loadByRollingChunked(current.index, 2, 5, sequentialCount = SEQUENTIAL_PAGES)
    }

    /**
     * Retries a page. This method is only called from user interaction on the viewer.
     */
    override fun retryPage(page: ReaderPage) {
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }

        val pageIndex = page.index
        val previous = downloadJobs[pageIndex]?.also { it.cancel() }
        // A cancelled download requeues its page when it ends
        scope.launch {
            previous?.join()
            internalLoadPage(page, force = true)
        }.trackAsDownloadJob(pageIndex)
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
                    chapterCache.putPageList(chapter.chapter, pagesToSave)
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
        val previous = downloadJobs[pageIndex]
        // A page another chain is already loading still counts towards this chunk, otherwise the chain moves on
        // and starts the next chunk while it's in flight
        // Not isActive, which is also false before a just launched job starts
        if (previous != null && !previous.isCancelled && !previous.isCompleted) return previous
        // A cancelled download keeps the page's status until it ends, so queue behind it
        if (previous == null && page.status !is Page.State.Queue && page.status !is Page.State.Error) return null

        val job = scope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            if (page.status != Page.State.Ready) internalLoadPage(page)
        }
        val claimed = if (previous == null) {
            downloadJobs.putIfAbsent(pageIndex, job) == null
        } else {
            downloadJobs.replace(pageIndex, previous, job)
        }
        if (!claimed) {
            job.cancel()
            return launchLoadOnce(page)
        }
        job.invokeOnCompletion { downloadJobs.remove(pageIndex, job) }
        job.start()
        return job
    }

    private fun List<ReaderPage>.loadByRollingChunked(
        index: Int,
        chunkStartSize: Int,
        chunkEndSize: Int,
        sequentialCount: Int = 0,
    ) {
        val items = this
        val shown = synchronized(chunkProcessLock) { shownPages.keys.toSet() }
        // When loading ahead isn't allowed, only the pages around the one being read load
        val window = if (canLoadAhead()) items.indices else (index - 1)..(index + LIMITED_PAGES_AHEAD)
        val chunks = buildList {
            items.getOrNull(index)?.let(::add)
            // Next before previous, as the first pages load one at a time
            items.getOrNull(index + 1)?.let(::add)
            items.getOrNull(index - 1)?.let(::add)
            // `items.lastIndex`, not the list being built, whose lastIndex is at most 2 here
            if (index < items.lastIndex - 1) {
                items.subList(index + 2, items.size).let(::addAll)
            }
            if (index > 1) {
                items.subList(0, index - 1).reversed().let(::addAll)
            }
        }
            .filter { it.index in window || it.index in shown }
            // Stable: the current page stays first
            .sortedByDescending { it.index == index || it.index in shown }
            .let { pages ->
                pages.take(sequentialCount).map(::listOf) +
                    pages.drop(sequentialCount).rollingChunked(chunkStartSize, chunkEndSize)
            }

        // Every visible page holder calls loadPage, so this runs concurrently
        synchronized(chunkProcessLock) {
            chunkProcessJob?.cancel()
            chunkProcessJob = scope.launch {
                for (chunk in chunks) {
                    // Under the lock and while active, or a replaced chain starts cancelled downloads
                    val jobs = synchronized(chunkProcessLock) {
                        ensureActive()
                        chunk.mapNotNull { page -> launchLoadOnce(page) }
                    }
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

            val file = chapterCache.getImageFile(imageUrl).takeUnless { force } ?: run {
                val live = page.downloadStream
                // Stale after a cancel until the first bytes, fooling isNearlyDownloaded
                page.progress = 0
                page.status = Page.State.DownloadImage
                try {
                    chapterCache.putImage(imageUrl, source.getImage(page), live)
                        .also { live?.finish() }
                } catch (e: Throwable) {
                    live?.finish(e)
                    throw e
                }
            }

            page.downloadStream = null
            page.stream = {
                try {
                    file.inputStream()
                } catch (e: FileNotFoundException) {
                    // Gone from the cache since, so it's fetched again and shown once it's back
                    page.status = Page.State.Queue
                    launchLoadOnce(page)
                    throw e
                }
            }
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

private const val SEQUENTIAL_PAGES = 3

private const val NEARLY_DOWNLOADED_PERCENT = 80

// progress survives a cancel
private val ReaderPage.isNearlyDownloaded: Boolean
    get() = status == Page.State.DownloadImage && progress >= NEARLY_DOWNLOADED_PERCENT
