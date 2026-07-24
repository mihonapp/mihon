package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It pauses active downloads while waiting for network recovery.
 */
class DownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val downloadManager: DownloadManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        if (downloadManager.queueState.value.isEmpty()) {
            return Result.failure()
        }

        var waitingForNetwork = false

        fun pauseForNetwork(status: DownloadNetworkStatus) {
            val reason = when (status) {
                DownloadNetworkStatus.NoWifi -> applicationContext.getString(R.string.download_notifier_text_only_wifi)
                DownloadNetworkStatus.NoNetwork -> applicationContext.getString(R.string.download_notifier_no_network)
                DownloadNetworkStatus.Available -> return
            }
            downloadManager.downloaderPauseForNetwork(reason)
            waitingForNetwork = downloadManager.queueState.value.isNotEmpty()
        }

        fun handleNetworkStatus(status: DownloadNetworkStatus, allowStart: Boolean) {
            when (status) {
                DownloadNetworkStatus.Available -> {
                    if (waitingForNetwork || allowStart) {
                        waitingForNetwork = false
                        downloadManager.downloaderStart()
                    }
                }
                DownloadNetworkStatus.NoNetwork,
                DownloadNetworkStatus.NoWifi,
                -> pauseForNetwork(status)
            }
        }

        val initialNetworkStatus = applicationContext.activeNetworkState()
            .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi.get())
        handleNetworkStatus(initialNetworkStatus, allowStart = true)

        if (!downloadManager.isRunning && !waitingForNetwork) {
            return Result.failure()
        }

        setForegroundSafely()

        try {
            coroutineScope {
                val networkStatusJob = combine(
                    applicationContext.networkStateFlow(),
                    downloadPreferences.downloadOnlyOverWifi.changes(),
                ) { networkState, requireWifi -> networkState.toDownloadNetworkStatus(requireWifi) }
                    .distinctUntilChanged()
                    .onEach { handleNetworkStatus(it, allowStart = false) }
                    .launchIn(this)

                try {
                    while (
                        !isStopped &&
                        downloadManager.queueState.value.isNotEmpty() &&
                        (downloadManager.isRunning || waitingForNetwork)
                    ) {
                        delay(1.seconds)
                    }
                } finally {
                    networkStatusJob.cancel()
                }
            }
        } finally {
            if (downloadManager.isRunning && downloadManager.queueState.value.isNotEmpty()) {
                val latestNetworkStatus = applicationContext.activeNetworkState()
                    .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi.get())
                pauseForNetwork(latestNetworkStatus)
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "Downloader"

        fun start(context: Context) {
            val downloadPreferences = Injekt.get<DownloadPreferences>()
            val request = OneTimeWorkRequestBuilder<DownloadJob>()
                .setConstraints(getConstraints(downloadPreferences.downloadOnlyOverWifi.get()))
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        private fun getConstraints(requireWifi: Boolean): Constraints {
            if (!requireWifi) {
                return Constraints(requiredNetworkType = NetworkType.CONNECTED)
            }

            val networkRequest = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            return Constraints.Builder()
                // The network request only applies to Android 9+, otherwise the network type is used.
                .setRequiredNetworkRequest(networkRequest, NetworkType.UNMETERED)
                .build()
        }
    }
}

internal sealed interface DownloadNetworkStatus {
    data object Available : DownloadNetworkStatus
    data object NoNetwork : DownloadNetworkStatus
    data object NoWifi : DownloadNetworkStatus
}

internal fun NetworkState.toDownloadNetworkStatus(requireWifi: Boolean): DownloadNetworkStatus {
    return when {
        !isOnline -> DownloadNetworkStatus.NoNetwork
        requireWifi && !isWifi -> DownloadNetworkStatus.NoWifi
        else -> DownloadNetworkStatus.Available
    }
}
