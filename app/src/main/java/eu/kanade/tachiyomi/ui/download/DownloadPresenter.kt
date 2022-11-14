package eu.kanade.tachiyomi.ui.download

import android.content.Context
import android.os.Bundle
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.injectLazy

class DownloadPresenter : BasePresenter<DownloadController>() {

    val downloadManager: DownloadManager by injectLazy()

    /**
     * Property to get the queue from the download manager.
     */
    private val downloadQueue: DownloadQueue
        get() = downloadManager.queue

    private val _state = MutableStateFlow(emptyList<DownloadHeaderItem>())
    val state = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launch {
            downloadQueue.updatedFlow()
                .catch { logcat(LogPriority.ERROR, it) }
                .map { downloads ->
                    downloads
                        .groupBy { it.source }
                        .map { entry ->
                            DownloadHeaderItem(entry.key.id, entry.key.name, entry.value.size).apply {
                                addSubItems(0, entry.value.map { DownloadItem(it, this) })
                            }
                        }
                }
                .collect { newList -> _state.update { newList } }
        }
    }

    fun getDownloadStatusFlow() = downloadQueue.statusFlow()

    fun getDownloadProgressFlow() = downloadQueue.progressFlow()

    /**
     * Pauses the download queue.
     */
    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    /**
     * Clears the download queue.
     */
    fun clearQueue(context: Context) {
        DownloadService.stop(context)
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<Download>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancelDownload(download: Download) {
        downloadManager.deletePendingDownload(download)
    }

    fun cancelDownloads(downloads: List<Download>) {
        downloadManager.deletePendingDownloads(*downloads.toTypedArray())
    }
}
