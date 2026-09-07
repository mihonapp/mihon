package mihon.desktop.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import kotlinx.coroutines.cancel
import java.io.IOException
import java.nio.file.Files

class DesktopDownloader(
    val store: DownloadStore,
    val diskProvider: DownloadDiskProvider,
    val networkHelper: DesktopNetworkHelper,
    val processManager: WindowsExtensionProcessManager? = null,
    val mutationPort: LibraryMutationPort? = null,
    val pageListFetcher: (suspend (sourceId: Long, chapterUrl: String) -> List<Page>)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val onDownloadCompleted: ((DesktopDownload) -> Unit)? = null,
    val onDownloadFailed: ((DesktopDownload, String) -> Unit)? = null,
) : AutoCloseable {
    private val _queueState = MutableStateFlow<List<DesktopDownload>>(emptyList())
    val queueState: StateFlow<List<DesktopDownload>> = _queueState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _speedBytesPerSec = MutableStateFlow(0.0)
    val speedBytesPerSec: StateFlow<Double> = _speedBytesPerSec.asStateFlow()

    private val queueMutex = Mutex()
    private var downloadJob: Job? = null

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
                store.save(updated)
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

    fun start(): Boolean {
        if (_isRunning.value) return false
        val hasPending = _queueState.value.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED
        }
        if (!hasPending) return false

        _isRunning.value = true
        downloadJob = scope.launch {
            try {
                runDownloadLoop()
            } finally {
                _isRunning.value = false
                _speedBytesPerSec.value = 0.0
            }
        }
        return true
    }

    fun pause() {
        downloadJob?.cancel()
        downloadJob = null
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
        store.save(_queueState.value)
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
        store.save(_queueState.value)
        start()
    }

    fun cancel(chapterId: Long) {
        val cancelled = _queueState.value.find { it.chapterId == chapterId }
        _queueState.update { list -> list.filterNot { it.chapterId == chapterId } }
        store.save(_queueState.value)

        cancelled?.let { download ->
            val tempDir = diskProvider.getTempChapterDir(download.sourceId, download.mangaTitle, download.chapterName)
            if (Files.exists(tempDir)) {
                try {
                    Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                } catch (_: Exception) {}
            }
        }
    }

    fun retry(chapterId: Long) {
        _queueState.update { list ->
            list.map { item ->
                if (item.chapterId == chapterId && item.status == DownloadStatus.ERROR) {
                    item.copy(status = DownloadStatus.QUEUED, error = null)
                } else {
                    item
                }
            }
        }
        store.save(_queueState.value)
        start()
    }

    fun clearCompleted() {
        _queueState.update { list -> list.filterNot { it.status == DownloadStatus.COMPLETED } }
        store.save(_queueState.value)
    }

    private suspend fun runDownloadLoop() {
        while (_isRunning.value) {
            val next = _queueState.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: break

            // Check disk space safety
            if (!diskProvider.checkDiskSpace()) {
                val errorMsg = "Insufficient disk space"
                updateDownload(next.chapterId) { it.copy(status = DownloadStatus.ERROR, error = errorMsg) }
                onDownloadFailed?.invoke(next, errorMsg)
                break
            }

            try {
                processDownload(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown download error"
                updateDownload(next.chapterId) { it.copy(status = DownloadStatus.ERROR, error = errorMsg) }
                onDownloadFailed?.invoke(next, errorMsg)
            }
        }
    }

    private suspend fun processDownload(download: DesktopDownload) {
        updateDownload(download.chapterId) { it.copy(status = DownloadStatus.DOWNLOADING) }

        // Fetch page list if not already populated
        var currentDownload = _queueState.value.first { it.chapterId == download.chapterId }
        val pages = if (currentDownload.pages.isEmpty()) {
            val fetchedPages = fetchPages(currentDownload.sourceId, currentDownload.chapterUrl)
            val downloadPages = fetchedPages.mapIndexed { index, page ->
                DownloadPage(
                    index = index,
                    url = page.url,
                    imageUrl = page.imageUrl,
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

        var totalBytes = currentDownload.bytesDownloaded
        val startTime = System.currentTimeMillis()
        var sessionBytes = 0L

        for (page in pages) {
            // Page-level resume: check if page file already exists
            val existingPageFile = diskProvider.getPageFile(tempDir, page.index)
            if (Files.exists(existingPageFile) && Files.size(existingPageFile) > 0L) {
                updatePageStatus(download.chapterId, page.index, PageStatus.READY, 1.0f)
                continue
            }

            updatePageStatus(download.chapterId, page.index, PageStatus.DOWNLOADING, 0.1f)

            val imageUrl = page.imageUrl ?: page.url
            val req = BrokerHttpRequest(
                method = "GET",
                url = imageUrl,
                headers = mapOf("Referer" to download.chapterUrl),
            )
            val bytes = networkHelper.downloadRawBytes(req)
            diskProvider.savePage(tempDir, page.index, bytes)

            totalBytes += bytes.size
            sessionBytes += bytes.size
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            if (elapsedSec > 0.1) {
                _speedBytesPerSec.value = sessionBytes / elapsedSec
            }

            updatePageStatus(download.chapterId, page.index, PageStatus.READY, 1.0f)
            updateDownload(download.chapterId) { it.copy(bytesDownloaded = totalBytes) }
        }

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
        if (processManager != null) {
            val chapter = SChapter(url = chapterUrl, name = "")
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
        store.save(_queueState.value)
        return updatedItem
    }

    private fun updatePageStatus(chapterId: Long, pageIndex: Int, status: PageStatus, progress: Float) {
        updateDownload(chapterId) { download ->
            val updatedPages = download.pages.map { p ->
                if (p.index == pageIndex) p.copy(status = status, progress = progress) else p
            }
            download.copy(pages = updatedPages)
        }
    }

    override fun close() {
        downloadJob?.cancel()
        downloadJob = null
        _isRunning.value = false
        scope.cancel()
    }
}
