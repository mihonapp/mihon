package eu.kanade.tachiyomi.ui.download

import android.view.MenuItem
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelDownloadItem(
    val mangaId: Long,
    val mangaTitle: String,
    val sourceName: String,
    val subItems: List<Download>,
    val initialTotal: Int, // Total chapters when the download batch started
) {
    val remainingChapters: Int get() = subItems.size

    // Downloaded chapters = initial total - remaining (since completed are removed from queue)
    val downloadedChapters: Int get() = (initialTotal - remainingChapters).coerceAtLeast(0)
    val totalChapters: Int get() = initialTotal
    val currentDownload: Download? get() = subItems.find { it.status == Download.State.DOWNLOADING }

    val overallProgress: Float get() {
        if (totalChapters == 0) return 0f
        val downloadedWeight = downloadedChapters.toFloat()
        val currentProgress = currentDownload?.progress?.div(100f) ?: 0f
        return (downloadedWeight + currentProgress) / totalChapters
    }

    val isActive: Boolean get() = currentDownload != null
    val hasError: Boolean get() = subItems.any { it.status == Download.State.ERROR }

    val statusText: String get() = when {
        hasError -> "Error"
        isActive -> "Downloading"
        subItems.all { it.status == Download.State.QUEUE } && subItems.isNotEmpty() -> "Queued"
        downloadedChapters == totalChapters -> "Completed"
        else -> "Pending"
    }
}

class DownloadQueueScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(emptyList<DownloadHeaderItem>())
    val state = _state.asStateFlow()

    private val _novelState = MutableStateFlow(emptyList<NovelDownloadItem>())
    val novelState = _novelState.asStateFlow()

    // Track the initial total for each manga to show accurate progress
    private val initialTotals = mutableMapOf<Long, Int>()

    val titleMaxLines = libraryPreferences.titleMaxLines().changes()
        .stateIn(screenModelScope, SharingStarted.Lazily, libraryPreferences.titleMaxLines().get())

    lateinit var controllerBinding: DownloadListBinding

    /**
     * Adapter containing the active downloads.
     */
    var adapter: DownloadAdapter? = null

    /**
     * Map of jobs for active downloads.
     */
    private val progressJobs = mutableMapOf<Download, Job>()

    val listener = object : DownloadAdapter.DownloadItemListener {
        /**
         * Called when an item is released from a drag.
         *
         * @param position The position of the released item.
         */
        override fun onItemReleased(position: Int) {
            val adapter = adapter ?: return
            val downloads = adapter.headerItems.flatMap { header ->
                adapter.getSectionItems(header).map { item ->
                    (item as DownloadItem).download
                }
            }
            reorder(downloads)
        }

        /**
         * Called when the menu item of a download is pressed
         *
         * @param position The position of the item
         * @param menuItem The menu Item pressed
         */
        override fun onMenuItemClick(position: Int, menuItem: MenuItem) {
            val item = adapter?.getItem(position) ?: return
            if (item is DownloadItem) {
                when (menuItem.itemId) {
                    R.id.move_to_top, R.id.move_to_bottom -> {
                        val headerItems = adapter?.headerItems ?: return
                        val newDownloads = mutableListOf<Download>()
                        headerItems.forEach { headerItem ->
                            headerItem as DownloadHeaderItem
                            if (headerItem == item.header) {
                                headerItem.removeSubItem(item)
                                if (menuItem.itemId == R.id.move_to_top) {
                                    headerItem.addSubItem(0, item)
                                } else {
                                    headerItem.addSubItem(item)
                                }
                            }
                            newDownloads.addAll(headerItem.subItems.map { it.download })
                        }
                        reorder(newDownloads)
                    }
                    R.id.move_to_top_series, R.id.move_to_bottom_series -> {
                        val (selectedSeries, otherSeries) = adapter?.currentItems
                            ?.filterIsInstance<DownloadItem>()
                            ?.map(DownloadItem::download)
                            ?.partition { item.download.mangaId == it.mangaId }
                            ?: Pair(emptyList(), emptyList())
                        if (menuItem.itemId == R.id.move_to_top_series) {
                            reorder(selectedSeries + otherSeries)
                        } else {
                            reorder(otherSeries + selectedSeries)
                        }
                    }
                    R.id.cancel_download -> {
                        cancel(listOf(item.download))
                    }
                    R.id.cancel_series -> {
                        val allDownloadsForSeries = adapter?.currentItems
                            ?.filterIsInstance<DownloadItem>()
                            ?.filter { item.download.mangaId == it.download.mangaId }
                            ?.map(DownloadItem::download)
                        if (!allDownloadsForSeries.isNullOrEmpty()) {
                            cancel(allDownloadsForSeries)
                        }
                    }
                }
            }
        }
    }

    init {
        screenModelScope.launch {
            downloadManager.queueState
                .map { downloads ->
                    val (novels, mangas) = downloads.partition { it.source.isNovelSource() }

                    val mangaItems = mangas.groupBy { it.source.id }
                        .map { (sourceId, downloads) ->
                            val source = downloads.first().source
                            DownloadHeaderItem(sourceId, source.name, downloads.size).apply {
                                addSubItems(0, downloads.map { DownloadItem(it, this) })
                            }
                        }

                    val novelItems = novels.groupBy { it.mangaId }
                        .map { (mangaId, downloads) ->
                            val mangaTitle = downloads.first().mangaTitle
                            val sourceName = downloads.first().source.name

                            // Track initial total - use max of current count and stored count
                            val currentCount = downloads.size
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
                                mangaTitle = mangaTitle,
                                sourceName = sourceName,
                                subItems = downloads,
                                initialTotal = initialTotal,
                            )
                        }

                    // Clean up initialTotals for manga no longer in queue
                    val currentMangaIds = novels.map { it.mangaId }.toSet()
                    initialTotals.keys.removeAll { it !in currentMangaIds }

                    Pair(mangaItems, novelItems)
                }
                .flowOn(Dispatchers.Default)
                .collect { (mangaItems, novelItems) ->
                    _state.update { mangaItems }
                    _novelState.update { novelItems }
                }
        }
    }

    override fun onDispose() {
        for (job in progressJobs.values) {
            job.cancel()
        }
        progressJobs.clear()
        adapter = null
    }

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getDownloadStatusFlow() = downloadManager.statusFlow()
    fun getDownloadProgressFlow() = downloadManager.progressFlow()

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<Download>) {
        reorderSubsetKeepingOthers(downloads)
    }

    fun reorderFullQueue(downloads: List<Download>) {
        downloadManager.reorderQueue(downloads)
    }

    private fun reorderSubsetKeepingOthers(downloads: List<Download>) {
        if (downloads.isEmpty()) return

        // Reorder only the provided downloads, while keeping everything else in-place.
        // This prevents, for example, sorting the Manga tab from dropping Novel downloads.
        val current = downloadManager.queueState.value
        val chapterIds = downloads.asSequence().map { it.chapterId }.toSet()
        val iterator = downloads.iterator()

        val merged = current.map { existing ->
            if (chapterIds.contains(existing.chapterId) && iterator.hasNext()) {
                iterator.next()
            } else {
                existing
            }
        }

        downloadManager.reorderQueue(merged)
    }

    fun cancel(downloads: List<Download>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(selector: (DownloadItem) -> R, reverse: Boolean = false) {
        val adapter = adapter ?: return
        val newDownloads = mutableListOf<Download>()
        adapter.headerItems.forEach { headerItem ->
            headerItem as DownloadHeaderItem
            headerItem.subItems = headerItem.subItems.sortedBy(selector).toMutableList().apply {
                if (reverse) {
                    reverse()
                }
            }
            newDownloads.addAll(headerItem.subItems.map { it.download })
        }
        reorderSubsetKeepingOthers(newDownloads)
    }

    fun reorderNovelQueueByGroupOrder(groupOrder: List<Long>) {
        // Build a reordered list of novel downloads based on the desired series (mangaId) ordering.
        val groups = novelState.value.associateBy { it.mangaId }
        val newDownloads = groupOrder.flatMap { mangaId ->
            groups[mangaId]?.subItems.orEmpty()
        }
        reorderSubsetKeepingOthers(newDownloads)
    }

    /**
     * Called when the status of a download changes.
     *
     * @param download the download whose status has changed.
     */
    fun onStatusChange(download: Download) {
        // Keep the row UI (e.g., error text) in sync with state changes.
        getHolder(download)?.notifyStatus()
        when (download.status) {
            Download.State.DOWNLOADING -> {
                launchProgressJob(download)
                // Initial update of the downloaded pages
                onUpdateDownloadedPages(download)
            }
            Download.State.DOWNLOADED -> {
                cancelProgressJob(download)
                onUpdateProgress(download)
                onUpdateDownloadedPages(download)
            }
            Download.State.ERROR -> cancelProgressJob(download)
            else -> {
                /* unused */
            }
        }
    }

    /**
     * Observe the progress of a download and notify the view.
     *
     * @param download the download to observe its progress.
     */
    private fun launchProgressJob(download: Download) {
        val job = screenModelScope.launch {
            while (download.pages == null) {
                delay(50)
            }

            val progressFlows = download.pages!!.map(Page::progressFlow)
            combine(progressFlows, Array<Int>::sum)
                .distinctUntilChanged()
                .debounce(50)
                .collectLatest {
                    onUpdateProgress(download)
                }
        }

        // Avoid leaking jobs
        progressJobs.remove(download)?.cancel()

        progressJobs[download] = job
    }

    /**
     * Unsubscribes the given download from the progress subscriptions.
     *
     * @param download the download to unsubscribe.
     */
    private fun cancelProgressJob(download: Download) {
        progressJobs.remove(download)?.cancel()
    }

    /**
     * Called when the progress of a download changes.
     *
     * @param download the download whose progress has changed.
     */
    private fun onUpdateProgress(download: Download) {
        getHolder(download)?.notifyProgress()
    }

    /**
     * Called when a page of a download is downloaded.
     *
     * @param download the download whose page has been downloaded.
     */
    fun onUpdateDownloadedPages(download: Download) {
        getHolder(download)?.notifyDownloadedPages()
    }

    /**
     * Returns the holder for the given download.
     *
     * @param download the download to find.
     * @return the holder of the download or null if it's not bound.
     */
    private fun getHolder(download: Download): DownloadHolder? {
        return controllerBinding.root.findViewHolderForItemId(download.chapterId) as? DownloadHolder
    }
}
