package eu.kanade.tachiyomi.ui.download

import android.os.Bundle
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Presenter of [DownloadFragment].
 */
class DownloadPresenter : BasePresenter<DownloadFragment>() {

    /**
     * Download manager.
     */
    @Inject lateinit var downloadManager: DownloadManager

    /**
     * Property to get the queue from the download manager.
     */
    val downloadQueue: DownloadQueue
        get() = downloadManager.queue

    /**
     * Map of subscriptions for active downloads.
     */
    private val progressSubscriptions by lazy { HashMap<Download, Subscription>() }

    /**
     * Subscription for status changes on downloads.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for downloaded pages for active downloads.
     */
    private var pageProgressSubscription: Subscription? = null

    companion object {
        /**
         * Id of the restartable that returns the download queue.
         */
        const val GET_DOWNLOAD_QUEUE = 1
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        restartableLatestCache(GET_DOWNLOAD_QUEUE,
                { Observable.just(downloadQueue) },
                { view, downloads -> view.onNextDownloads(downloads) },
                { view, error -> Timber.e(error.message) })

        if (savedState == null) {
            start(GET_DOWNLOAD_QUEUE)
        }
    }

    override fun onTakeView(view: DownloadFragment) {
        super.onTakeView(view)

        statusSubscription = downloadQueue.getStatusObservable()
                .startWith(downloadQueue.getActiveDownloads())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { processStatus(it, view) }

        add(statusSubscription)

        pageProgressSubscription = downloadQueue.getProgressObservable()
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { view.onUpdateDownloadedPages(it) }

        add(pageProgressSubscription)
    }

    override fun onDropView() {
        destroySubscriptions()
        super.onDropView()
    }

    /**
     * Process the status of a download when its status has changed and notify the view.
     *
     * @param download the download whose status has changed.
     * @param view the view.
     */
    private fun processStatus(download: Download, view: DownloadFragment) {
        when (download.status) {
            Download.DOWNLOADING -> {
                observeProgress(download, view)
                // Initial update of the downloaded pages
                view.onUpdateDownloadedPages(download)
            }
            Download.DOWNLOADED -> {
                unsubscribeProgress(download)
                view.onUpdateProgress(download)
                view.onUpdateDownloadedPages(download)
            }
            Download.ERROR -> unsubscribeProgress(download)
        }
    }

    /**
     * Observe the progress of a download and notify the view.
     *
     * @param download the download to observe its progress.
     * @param view the view.
     */
    private fun observeProgress(download: Download, view: DownloadFragment) {
        val subscription = Observable.interval(50, TimeUnit.MILLISECONDS, Schedulers.newThread())
                // Get the sum of percentages for all the pages.
                .flatMap {
                    Observable.from(download.pages)
                            .map { it.progress }
                            .reduce { x, y -> x + y }
                }
                // Keep only the latest emission to avoid backpressure.
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { progress ->
                    // Update the view only if the progress has changed.
                    if (download.totalProgress != progress) {
                        download.totalProgress = progress
                        view.onUpdateProgress(download)
                    }
                }

        // Avoid leaking subscriptions
        progressSubscriptions.remove(download)?.unsubscribe()

        progressSubscriptions.put(download, subscription)
    }

    /**
     * Unsubscribes the given download from the progress subscriptions.
     *
     * @param download the download to unsubscribe.
     */
    private fun unsubscribeProgress(download: Download) {
        progressSubscriptions.remove(download)?.unsubscribe()
    }

    /**
     * Destroys all the subscriptions of the presenter.
     */
    private fun destroySubscriptions() {
        for (subscription in progressSubscriptions.values) {
            subscription.unsubscribe()
        }
        progressSubscriptions.clear()

        remove(pageProgressSubscription)
        remove(statusSubscription)
    }

    /**
     * Clears the download queue.
     */
    fun clearQueue() {
        downloadQueue.clear()
        start(GET_DOWNLOAD_QUEUE)
    }

}
