package eu.kanade.tachiyomi.ui.more

import android.os.Bundle
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MorePresenter(
    private val downloadManager: DownloadManager = Injekt.get(),
    preferencesHelper: PreferencesHelper = Injekt.get(),
) : BasePresenter<MoreController>() {

    val downloadedOnly = preferencesHelper.downloadedOnly().asState()
    val incognitoMode = preferencesHelper.incognitoMode().asState()

    private var _state: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _state.asStateFlow()

    private var isDownloading: Boolean = false
    private var downloadQueueSize: Int = 0
    private var untilDestroySubscriptions = CompositeSubscription()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if (untilDestroySubscriptions.isUnsubscribed) {
            untilDestroySubscriptions = CompositeSubscription()
        }

        initDownloadQueueSummary()
    }

    override fun onDestroy() {
        super.onDestroy()
        untilDestroySubscriptions.unsubscribe()
    }

    private fun initDownloadQueueSummary() {
        // Handle running/paused status change
        DownloadService.runningRelay
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeUntilDestroy { isRunning ->
                isDownloading = isRunning
                updateDownloadQueueState()
            }

        // Handle queue progress updating
        downloadManager.queue.getUpdatedObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeUntilDestroy {
                downloadQueueSize = it.size
                updateDownloadQueueState()
            }
    }

    private fun updateDownloadQueueState() {
        presenterScope.launchIO {
            val pendingDownloadExists = downloadQueueSize != 0
            _state.value = when {
                !pendingDownloadExists -> DownloadQueueState.Stopped
                !isDownloading && !pendingDownloadExists -> DownloadQueueState.Paused(0)
                !isDownloading && pendingDownloadExists -> DownloadQueueState.Paused(downloadQueueSize)
                else -> DownloadQueueState.Downloading(downloadQueueSize)
            }
        }
    }

    private fun <T> Observable<T>.subscribeUntilDestroy(onNext: (T) -> Unit): Subscription {
        return subscribe(onNext).also { untilDestroySubscriptions.add(it) }
    }
}

sealed class DownloadQueueState {
    object Stopped : DownloadQueueState()
    data class Paused(val pending: Int) : DownloadQueueState()
    data class Downloading(val pending: Int) : DownloadQueueState()
}
