package eu.kanade.tachiyomi.ui.download

import android.os.Bundle
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.*

/**
 * Presenter of [DownloadFragment].
 */
class DownloadPresenter : BasePresenter<DownloadFragment>() {

    /**
     * Download manager.
     */
    val downloadManager: DownloadManager by injectLazy()

    /**
     * Property to get the queue from the download manager.
     */
    val downloadQueue: DownloadQueue
        get() = downloadManager.queue

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        
        Observable.just(ArrayList(downloadQueue))
                .doOnNext { syncQueue(it) }
                .subscribeLatestCache({ view, downloads ->
                    view.onNextDownloads(downloads)
                }, { view, error ->
                    Timber.e(error)
                })
    }

    private fun syncQueue(queue: MutableList<Download>) {
        add(downloadQueue.getRemovedObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { download ->
                    val position = queue.indexOf(download)
                    if (position != -1) {
                        queue.removeAt(position)

                        @Suppress("DEPRECATION")
                        view?.onDownloadRemoved(position)
                    }
                })
    }

    fun getStatusObservable(): Observable<Download> {
        return downloadQueue.getStatusObservable()
                .startWith(downloadQueue.getActiveDownloads())
    }

    fun getProgressObservable(): Observable<Download> {
        return downloadQueue.getProgressObservable()
                .onBackpressureBuffer()
    }

    /**
     * Clears the download queue.
     */
    fun clearQueue() {
        downloadManager.clearQueue()
    }

}
