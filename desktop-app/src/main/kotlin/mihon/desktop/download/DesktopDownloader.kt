package mihon.desktop.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DesktopDownloader(
    val store: DownloadStore,
    val diskProvider: DownloadDiskProvider,
    val networkHelper: DesktopNetworkHelper,
    val processManager: WindowsExtensionProcessManager? = null,
    val sourceManager: mihon.desktop.extension.DesktopSourceManager? = null,
    val mutationPort: LibraryMutationPort? = null,
    val pageListFetcher: (suspend (sourceId: Long, chapterUrl: String) -> List<Page>)? = null,
    val downloadParallelism: () -> Int = { 1 },
    val pageParallelism: () -> Int = { 1 },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val onDownloadCompleted: ((DesktopDownload) -> Unit)? = null,
    val onDownloadFailed: ((DesktopDownload, String) -> Unit)? = null,
    val onDownloadProgress: ((DesktopDownload) -> Unit)? = null,
    val sourceParallelism: () -> Int = { 1 },
) : AutoCloseable {
    private val _queueState = MutableStateFlow<List<DesktopDownload>>(emptyList())
    val queueState: StateFlow<List<DesktopDownload>> = _queueState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _speedBytesPerSec = MutableStateFlow(0.0)
    val speedBytesPerSec: StateFlow<Double> = _speedBytesPerSec.asStateFlow()

    private val queueMutex = Mutex()
    private val persistenceLock = Any()
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()
    private var lastClaimedSource: Long? = null

    private fun persistQueue() = synchronized(persistenceLock) {
        try {
            store.save(_queueState.value)
            _storageError.value = null
        } catch (error: Exception) {
            _storageError.value = "下载队列无法保存，已停止调度：${error.message}"
            _isRunning.value = false
            downloadJob?.cancel()
            throw error
        }
    }
    private val activeDownloadJobs = ConcurrentHashMap<Long, Job>()
    private val sessionBytes = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)
    private var downloadJob: Job? = null
    private var sessionStartedAt = 0L

    init {
        val restored = store.restore()
        _queueState.value = restored
    }

    suspend fun enqueue(
        sourceId: Long,
        mangaId: Long,
        mangaTitle: String,
        chapters: List<LibraryChapter>,
        autoStart: Boolean = true,
    ) {
        queueMutex.withLock {
            val current = _queueState.value
            val newItems = mutableListOf<DesktopDownload>()

            for (chapter in chapters) {
                // Deduplicate: check if already in queue or already downloaded
                if (current.any { it.chapterId == chapter.id }) continue
                if (diskProvider.isChapterDownloaded(sourceId, mangaTitle, chapter.name)) continue

                newItems.add(
                    DesktopDownload(
                        chapterId = chapter.id,
                        mangaId = mangaId,
                        sourceId = sourceId,
                        mangaTitle = mangaTitle,
                        chapterName = chapter.name,
                        chapterUrl = chapter.url,
                        status = DownloadStatus.QUEUED,
                    ),
                )
            }

            if (newItems.isNotEmpty()) {
                val updated = current + newItems
                _queueState.value = updated
                persistQueue()
            }
        }

        if (autoStart) {
            start()
        }
    }

    suspend fun enqueue(manga: LibraryManga, chapters: List<LibraryChapter>, autoStart: Boolean = true) {
        enqueue(manga.sourceId, manga.id, manga.title, chapters, autoStart)
    }

    suspend fun enqueue(manga: MangaDetails, chapters: List<LibraryChapter>, autoStart: Boolean = true) {
        enqueue(manga.sourceId, manga.id, manga.title, chapters, autoStart)
    }

    suspend fun checkAndDownloadAhead(
        sourceId: Long,
        mangaId: Long,
        mangaTitle: String,
        currentChapter: LibraryChapter,
        allChapters: List<LibraryChapter>,
        count: Int,
        autoStart: Boolean = true,
    ) {
        if (count <= 0) return
        val sorted = allChapters.sortedWith(compareBy<LibraryChapter> { it.chapterNumber }.thenBy { it.sourceOrder })
        val currentIndex = sorted.indexOfFirst { it.id == currentChapter.id }
        if (currentIndex < 0) return

        val nextChapters = sorted.drop(currentIndex + 1)
            .filter { !it.read && !diskProvider.isChapterDownloaded(sourceId, mangaTitle, it.name) }
            .take(count)

        if (nextChapters.isNotEmpty()) {
            enqueue(sourceId, mangaId, mangaTitle, nextChapters, autoStart = autoStart)
        }
    }

    suspend fun checkAndDownloadAhead(
        manga: LibraryManga,
        currentChapter: LibraryChapter,
        allChapters: List<LibraryChapter>,
        count: Int,
        autoStart: Boolean = true,
    ) = checkAndDownloadAhead(manga.sourceId, manga.id, manga.title, currentChapter, allChapters, count, autoStart)

    suspend fun checkAndDownloadAhead(
        manga: MangaDetails,
        currentChapter: LibraryChapter,
        allChapters: List<LibraryChapter>,
        count: Int,
        autoStart: Boolean = true,
    ) = checkAndDownloadAhead(manga.sourceId, manga.id, manga.title, currentChapter, allChapters, count, autoStart)

    fun deleteDownloadedChapter(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        return diskProvider.deleteChapter(sourceId, mangaTitle, chapterName)
    }

    fun deleteDownloadedChapter(manga: LibraryManga, chapter: LibraryChapter): Boolean =
        deleteDownloadedChapter(manga.sourceId, manga.title, chapter.name)

    fun deleteDownloadedChapter(manga: MangaDetails, chapter: LibraryChapter): Boolean =
        deleteDownloadedChapter(manga.sourceId, manga.title, chapter.name)

    @Synchronized
    fun start(): Boolean {
        if (_isRunning.value) return false
        val hasPending = _queueState.value.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED
        }
        if (!hasPending) return false

        _isRunning.value = true
        sessionBytes.set(0L)
        sessionStartedAt = System.currentTimeMillis()
        val generation = sessionGeneration.incrementAndGet()
        val previousJob = downloadJob
        downloadJob = scope.launch {
            try {
                previousJob?.join()
                runDownloadLoop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _storageError.value = "下载调度已停止：${error.message}"
            } finally {
                if (sessionGeneration.get() == generation) {
                    _isRunning.value = false
                    _speedBytesPerSec.value = 0.0
                }
            }
        }
        return true
    }

    fun pause() {
        sessionGeneration.incrementAndGet()
        downloadJob?.cancel()
        _isRunning.value = false
        _speedBytesPerSec.value = 0.0

        _queueState.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.DOWNLOADING) {
                    item.copy(status = DownloadStatus.PAUSED)
                } else {
                    item
                }
            }
        }
        persistQueue()
    }

    fun resume() {
        _queueState.update { list ->
            list.map { item ->
                if (item.status == DownloadStatus.PAUSED) {
                    item.copy(status = DownloadStatus.QUEUED)
                } else {
                    item
                }
            }
        }
        persistQueue()
        start()
    }

    fun cancel(chapterId: Long) {
        val job = activeDownloadJobs.remove(chapterId)
        job?.cancel()
        val cancelled = _queueState.value.find { it.chapterId == chapterId }
        _queueState.update { list -> list.filterNot { it.chapterId == chapterId } }
        persistQueue()
        cancelled?.let { download ->
            scope.launch {
                job?.join()
                runCatching {
                    diskProvider.deleteTempChapter(download.sourceId, download.mangaTitle, download.chapterName)
                }
                    .onFailure { _storageError.value = "下载临时文件无法清理：${it.message}" }
            }
        }
    }
    fun retry(chapterId: Long) {
        _queueState.update { list ->
            list.map { item ->
                if (item.chapterId == chapterId && item.status == DownloadStatus.ERROR) {
                    item.copy(
                        status = DownloadStatus.QUEUED,
                        error = null,
                        pages = item.pages.map { page ->
                            if (page.status ==
                                PageStatus.ERROR
                            ) {
                                page.copy(status = PageStatus.QUEUE, error = null)
                            } else {
                                page
                            }
                        },
                    )
                } else {
                    item
                }
            }
        }
        persistQueue()
        start()
    }

    fun clearCompleted() {
        _queueState.update { list -> list.filterNot { it.status == DownloadStatus.COMPLETED } }
        persistQueue()
    }

    private suspend fun runDownloadLoop() = supervisorScope {
        val workers = List(downloadParallelism().coerceIn(1, 16)) {
            launch {
                while (_isRunning.value) {
                    val next = claimNextDownload() ?: break
                    val chapterJob = launch(start = CoroutineStart.LAZY) {
                        if (!diskProvider.checkDiskSpace()) {
                            val errorMessage = "Insufficient disk space"
                            updateDownload(next.chapterId) {
                                it.copy(status = DownloadStatus.ERROR, error = errorMessage)
                            }
                            onDownloadFailed?.invoke(next, errorMessage)
                            return@launch
                        }

                        try {
                            processDownload(next)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            val errorMessage = error.message ?: "Unknown download error"
                            val failed = updateDownload(next.chapterId) {
                                it.copy(status = DownloadStatus.ERROR, error = errorMessage)
                            }
                            onDownloadFailed?.invoke(failed ?: next, errorMessage)
                        }
                    }
                    activeDownloadJobs[next.chapterId] = chapterJob
                    chapterJob.start()
                    try {
                        chapterJob.join()
                    } finally {
                        activeDownloadJobs.remove(next.chapterId, chapterJob)
                    }
                }
            }
        }
        workers.forEach { it.join() }
    }

    @Synchronized
    private fun claimNextDownload(): DesktopDownload? {
        while (true) {
            val current = _queueState.value
            val activeBySource = current.filter {
                it.status == DownloadStatus.DOWNLOADING
            }.groupingBy { it.sourceId }.eachCount()
            val eligible = current.filter {
                it.status == DownloadStatus.QUEUED &&
                    (activeBySource[it.sourceId] ?: 0) < sourceParallelism().coerceIn(1, 16)
            }
            val sources = current.map { it.sourceId }.distinct()
            val after = sources.indexOfFirst { it == lastClaimedSource } + 1
            val rotation = sources.drop(after) + sources.take(after)
            val source = rotation.firstOrNull { id -> eligible.any { it.sourceId == id } } ?: return null
            val next = eligible.first { it.sourceId == source }
            val claimed = next.copy(status = DownloadStatus.DOWNLOADING, error = null)
            val updated = current.map { item -> if (item.chapterId == next.chapterId) claimed else item }
            if (_queueState.compareAndSet(current, updated)) {
                lastClaimedSource = next.sourceId
                persistQueue()
                return claimed
            }
        }
    }

    private suspend fun processDownload(download: DesktopDownload) {
        // Fetch page list if not already populated
        val currentDownload = _queueState.value.first { it.chapterId == download.chapterId }
        val pages = if (currentDownload.pages.isEmpty()) {
            val fetchedPages = fetchPages(currentDownload.sourceId, currentDownload.chapterUrl)
            val downloadPages = fetchedPages.distinctBy { it.index }.mapIndexed { index, page ->
                DownloadPage(
                    index = index,
                    url = page.url,
                    imageUrl = page.imageUrl,
                    headers = page.headers,
                    status = PageStatus.QUEUE,
                )
            }
            updateDownload(currentDownload.chapterId) { it.copy(pages = downloadPages) }
            downloadPages
        } else {
            currentDownload.pages
        }

        if (pages.isEmpty()) {
            throw IOException("Page list is empty for chapter ${download.chapterName}")
        }

        val tempDir = diskProvider.getTempChapterDir(
            download.sourceId,
            download.mangaTitle,
            download.chapterName,
        )
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir)
        }

        diskProvider.cleanPartialPages(tempDir)
        // A previous run may have published the images before database registration failed.
        // Only reuse pages the saved queue marked ready, and validate their bytes again.
        val publishedDir = diskProvider.getChapterDir(download.sourceId, download.mangaTitle, download.chapterName)
        pages.filter { it.status == PageStatus.READY }.forEach { page ->
            val temporaryPage = diskProvider.getPageFile(tempDir, page.index)
            val publishedPage = diskProvider.getPageFile(publishedDir, page.index)
            if (!diskProvider.isValidPage(temporaryPage) && diskProvider.isValidPage(publishedPage)) {
                Files.copy(publishedPage, temporaryPage, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val validPages = pages.filter { diskProvider.isValidPage(diskProvider.getPageFile(tempDir, it.index)) }
        val totalBytes = AtomicLong(validPages.sumOf { Files.size(diskProvider.getPageFile(tempDir, it.index)) })
        updateDownload(download.chapterId) { item ->
            item.copy(
                bytesDownloaded = totalBytes.get(),
                pages = item.pages.map { page ->
                    if (validPages.any { it.index == page.index }) {
                        page.copy(status = PageStatus.READY, progress = 1f, error = null)
                    } else {
                        page.copy(status = PageStatus.QUEUE, progress = 0f, error = null)
                    }
                },
            )
        }
        val pageFailures = ConcurrentHashMap<Int, Exception>()
        val pageSemaphore = Semaphore(pageParallelism().coerceIn(1, 32))
        coroutineScope {
            pages.map { page ->
                async {
                    pageSemaphore.withPermit {
                        if (validPages.any { it.index == page.index }) {
                            updatePageStatus(download.chapterId, page.index, PageStatus.READY, 1.0f)
                            return@withPermit
                        }

                        updatePageStatus(download.chapterId, page.index, PageStatus.DOWNLOADING, 0.1f)
                        try {
                            val bytes = mihon.desktop.extension.downloadSourcePage(
                                download.sourceId,
                                Page(page.index, page.url, page.imageUrl, page.headers),
                                download.chapterUrl,
                                networkHelper,
                                sourceManager,
                                processManager,
                                priority = mihon.extension.ipc.RequestPriority.BACKGROUND,
                            )
                            currentCoroutineContext().ensureActive()
                            diskProvider.savePage(tempDir, page.index, bytes)

                            val downloadedBytes = totalBytes.addAndGet(bytes.size.toLong())
                            val allSessionBytes = sessionBytes.addAndGet(bytes.size.toLong())
                            val elapsedSeconds = (System.currentTimeMillis() - sessionStartedAt) / 1000.0
                            if (elapsedSeconds > 0.1) {
                                _speedBytesPerSec.value = allSessionBytes / elapsedSeconds
                            }

                            updatePageStatus(download.chapterId, page.index, PageStatus.READY, 1.0f)
                            val progress = updateDownload(download.chapterId) {
                                it.copy(bytesDownloaded = downloadedBytes)
                            }
                            progress?.let { onDownloadProgress?.invoke(it) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            updatePageStatus(
                                download.chapterId,
                                page.index,
                                PageStatus.ERROR,
                                0f,
                                error.message,
                            )
                            pageFailures[page.index] = error
                        }
                    }
                }
            }.forEach { it.await() }
        }

        if (pageFailures.isNotEmpty()) {
            val first = pageFailures.minBy { it.key }
            throw IOException(
                "${pageFailures.size} page(s) failed; page ${first.key + 1}: ${first.value.message}",
                first.value,
            )
        }

        currentCoroutineContext().ensureActive()
        // Finalize chapter atomically and register in database
        diskProvider.finalizeChapter(
            sourceId = download.sourceId,
            mangaId = download.mangaId,
            chapterId = download.chapterId,
            mangaTitle = download.mangaTitle,
            chapterName = download.chapterName,
            totalPages = pages.size,
            mutationPort = mutationPort,
        )

        val completedDownload = updateDownload(download.chapterId) {
            it.copy(status = DownloadStatus.COMPLETED, progress = 1.0f)
        }
        completedDownload?.let { onDownloadCompleted?.invoke(it) }
    }

    private suspend fun fetchPages(sourceId: Long, chapterUrl: String): List<Page> {
        if (pageListFetcher != null) {
            return pageListFetcher.invoke(sourceId, chapterUrl)
        }
        val chapter = SChapter(url = chapterUrl, name = "")
        if (sourceManager != null) {
            return sourceManager.getPageList(sourceId, chapter)
        }
        if (processManager != null) {
            return processManager.getPageList(sourceId, chapter)
        }
        throw IllegalStateException("No page list fetcher available")
    }

    private fun updateDownload(chapterId: Long, transform: (DesktopDownload) -> DesktopDownload): DesktopDownload? {
        var updatedItem: DesktopDownload? = null
        _queueState.update { list ->
            list.map { item ->
                if (item.chapterId == chapterId) {
                    val updated = transform(item)
                    val readyCount = updated.pages.count { it.status == PageStatus.READY }
                    val progress = if (updated.pages.isNotEmpty()) readyCount.toFloat() / updated.pages.size else 0f
                    val finalItem = updated.copy(progress = progress)
                    updatedItem = finalItem
                    finalItem
                } else {
                    item
                }
            }
        }
        persistQueue()
        return updatedItem
    }

    private fun updatePageStatus(
        chapterId: Long,
        pageIndex: Int,
        status: PageStatus,
        progress: Float,
        error: String? = null,
    ) {
        updateDownload(chapterId) { download ->
            val updatedPages = download.pages.map { p ->
                if (p.index == pageIndex) p.copy(status = status, progress = progress, error = error) else p
            }
            download.copy(pages = updatedPages)
        }
    }

    suspend fun shutdown() {
        close()
        scope.coroutineContext[Job]?.join()
    }

    override fun close() {
        downloadJob?.cancel()
        activeDownloadJobs.values.forEach(Job::cancel)
        activeDownloadJobs.clear()
        downloadJob = null
        _isRunning.value = false
        scope.cancel()
    }
}
