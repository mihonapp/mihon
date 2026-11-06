package eu.kanade.tachiyomi.ui.download

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class DownloadQueueViewModel(
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val state: StateFlow<List<DownloadQueueSection>> = downloadManager.queueState
        .map { downloads ->
            downloads
                .groupBy { it.source }
                .map { (source, downloads) -> DownloadQueueSection(source.id, source.name, downloads) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), false)

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        viewModelScope.launch { downloadManager.pauseDownloads() }
    }

    fun clearQueue() {
        viewModelScope.launch { downloadManager.clearQueue() }
    }

    fun reorder(downloads: List<Download>) {
        viewModelScope.launch { downloadManager.reorderQueue(downloads) }
    }

    fun cancel(downloads: List<Download>) {
        viewModelScope.launch { downloadManager.cancelQueuedDownloads(downloads) }
    }

    /**
     * Moves [download] to the top or bottom of its section.
     */
    fun move(download: Download, toTop: Boolean) {
        reorder(
            state.value.flatMap { section ->
                if (download in section.downloads) {
                    val others = section.downloads - download
                    if (toTop) listOf(download) + others else others + download
                } else {
                    section.downloads
                }
            },
        )
    }

    /**
     * Moves all the downloads of the manga of [download] to the top or bottom of the queue.
     */
    fun moveSeries(download: Download, toTop: Boolean) {
        val (selectedSeries, otherSeries) = state.value
            .flatMap { it.downloads }
            .partition { it.manga.id == download.manga.id }
        reorder(if (toTop) selectedSeries + otherSeries else otherSeries + selectedSeries)
    }

    fun cancelSeries(download: Download) {
        val allDownloadsForSeries = state.value
            .flatMap { it.downloads }
            .filter { it.manga.id == download.manga.id }
        if (allDownloadsForSeries.isNotEmpty()) {
            cancel(allDownloadsForSeries)
        }
    }

    /**
     * Sorts the downloads of every section by [selector].
     */
    fun <R : Comparable<R>> reorderQueue(selector: (Download) -> R?, reverse: Boolean = false) {
        reorder(
            state.value.flatMap { section ->
                section.downloads.sortedBy(selector).let { if (reverse) it.reversed() else it }
            },
        )
    }
}

/**
 * Downloads of the queue from one source.
 */
@Immutable
data class DownloadQueueSection(
    val id: Long,
    val name: String,
    val downloads: List<Download>,
)
