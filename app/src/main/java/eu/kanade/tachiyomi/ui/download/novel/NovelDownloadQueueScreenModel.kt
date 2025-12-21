package eu.kanade.tachiyomi.ui.download.novel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.isNovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Represents a novel download with grouped chapters.
 * Shows as a single entry with progress like "5/20 chapters"
 *
 * Note: Since completed downloads are removed from the queue immediately,
 * we track initialTotal separately to show accurate progress.
 */
data class NovelDownloadItem(
    val mangaId: Long,
    val mangaTitle: String,
    val sourceName: String,
    val downloads: List<Download>,
    val initialTotal: Int, // Total chapters when the download batch started
) {
    val remainingChapters: Int get() = downloads.size

    // Downloaded chapters = initial total - remaining (since completed are removed from queue)
    val downloadedChapters: Int get() = (initialTotal - remainingChapters).coerceAtLeast(0)
    val totalChapters: Int get() = initialTotal
    val pendingChapters: Int get() = downloads.count { it.status == Download.State.QUEUE }
    val currentDownload: Download? get() = downloads.find { it.status == Download.State.DOWNLOADING }

    val overallProgress: Float get() {
        if (totalChapters == 0) return 0f
        val downloadedWeight = downloadedChapters.toFloat()
        val currentProgress = currentDownload?.progress?.div(100f) ?: 0f
        return (downloadedWeight + currentProgress) / totalChapters
    }

    val isActive: Boolean get() = currentDownload != null
    val isPaused: Boolean get() = downloads.all { it.status == Download.State.QUEUE } && downloads.isNotEmpty()
    val hasError: Boolean get() = downloads.any { it.status == Download.State.ERROR }

    val statusText: String get() = when {
        hasError -> "Error"
        isActive -> "Downloading"
        isPaused -> "Queued"
        downloadedChapters == totalChapters -> "Completed"
        else -> "Pending"
    }
}

class NovelDownloadQueueScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow<List<NovelDownloadItem>>(emptyList())
    val state = _state.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    // Track the initial total for each manga to show accurate progress
    private val initialTotals = mutableMapOf<Long, Int>()

    init {
        screenModelScope.launch {
            // Filter only novel downloads and group by manga
            downloadManager.queueState
                .map { downloads -> downloads.filter { it.source.isNovelSource() } }
                .map { novelDownloads ->
                    // Clean up initialTotals for manga no longer in queue
                    val currentMangaIds = novelDownloads.asSequence().map { it.mangaId }.toSet()
                    initialTotals.keys.removeAll { it !in currentMangaIds }

                    val items = novelDownloads
                        .groupBy { it.mangaId }
                        .map { (mangaId, mangaDownloads) ->
                            // Track initial total - use max of current count and stored count
                            val currentCount = mangaDownloads.size
                            val storedTotal = initialTotals[mangaId] ?: 0

                            // If current count is greater, this is a new batch or first time seeing this
                            val initialTotal = if (currentCount > storedTotal) {
                                initialTotals[mangaId] = currentCount
                                currentCount
                            } else {
                                storedTotal
                            }

                            NovelDownloadItem(
                                mangaId = mangaId,
                                mangaTitle = mangaDownloads.first().mangaTitle,
                                sourceName = mangaDownloads.first().source.name,
                                downloads = mangaDownloads,
                                initialTotal = initialTotal,
                            )
                        }

                    items to downloadManager.isRunning
                }
                // Avoid doing heavy list operations (filter/groupBy) on the main thread
                .flowOn(Dispatchers.Default)
                .collectLatest { (items, isRunning) ->
                    _state.value = items
                    _isRunning.value = isRunning
                }
        }

        // Track running state
        screenModelScope.launch {
            downloadManager.isDownloaderRunning
                .collectLatest { running ->
                    _isRunning.value = running
                }
        }
    }

    /**
     * Start the download queue
     */
    fun startDownloads() {
        downloadManager.startDownloads()
    }

    /**
     * Pause all downloads
     */
    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    /**
     * Cancel downloads for a specific novel
     */
    fun cancelNovelDownloads(mangaId: Long) {
        val downloads = _state.value
            .find { it.mangaId == mangaId }
            ?.downloads
            ?: return

        downloadManager.cancelQueuedDownloads(downloads)
    }

    /**
     * Cancel all novel downloads
     */
    fun cancelAllNovelDownloads() {
        val allDownloads = _state.value.flatMap { it.downloads }
        downloadManager.cancelQueuedDownloads(allDownloads)
    }

    /**
     * Move novel downloads to top of queue
     */
    fun moveToTop(mangaId: Long) {
        val novelItem = _state.value.find { it.mangaId == mangaId } ?: return
        val otherDownloads = downloadManager.queueState.value.filter { it.mangaId != mangaId }
        downloadManager.reorderQueue(novelItem.downloads + otherDownloads)
    }

    /**
     * Move novel downloads to bottom of queue
     */
    fun moveToBottom(mangaId: Long) {
        val novelItem = _state.value.find { it.mangaId == mangaId } ?: return
        val otherDownloads = downloadManager.queueState.value.filter { it.mangaId != mangaId }
        downloadManager.reorderQueue(otherDownloads + novelItem.downloads)
    }

    /**
     * Clear completed downloads from the queue
     */
    fun clearCompletedNovelDownloads() {
        val allNovelDownloads = _state.value.flatMap { it.downloads }
        val completedDownloads = allNovelDownloads.filter { it.status == Download.State.DOWNLOADED }
        downloadManager.cancelQueuedDownloads(completedDownloads)
    }
}
