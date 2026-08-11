package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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

        val workerId = id
        val waitingForNetwork = AtomicBoolean()

        fun pauseForNetwork(status: DownloadNetworkStatus) {
            val reason = when (status) {
                DownloadNetworkStatus.NoWifi -> applicationContext.getString(R.string.download_notifier_text_only_wifi)
                DownloadNetworkStatus.NoNetwork -> applicationContext.getString(R.string.download_notifier_no_network)
                DownloadNetworkStatus.Available -> return
            }
            waitingForNetwork.set(downloadManager.queueState.value.isNotEmpty())
            downloadManager.downloaderPauseForNetwork(reason)
        }

        fun handleNetworkStatus(status: DownloadNetworkStatus, allowStart: Boolean) {
            synchronized(workerLock) {
                if (activeWorkerId != workerId) return

                when (status) {
                    DownloadNetworkStatus.Available -> {
                        if (waitingForNetwork.get() || allowStart) {
                            downloadManager.downloaderStart()
                            waitingForNetwork.set(false)
                        }
                    }
                    DownloadNetworkStatus.NoNetwork,
                    DownloadNetworkStatus.NoWifi,
                    -> pauseForNetwork(status)
                }
            }
        }

        synchronized(workerLock) {
            activeWorkerId = workerId
        }

        try {
            val initialNetworkStatus = applicationContext.activeNetworkState()
                .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi.get())
            handleNetworkStatus(initialNetworkStatus, allowStart = true)

            if (!downloadManager.isRunning && !waitingForNetwork.get()) {
                return Result.failure()
            }

            setForegroundSafely()

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
                        activeWorkerId == workerId &&
                        downloadManager.queueState.value.isNotEmpty() &&
                        (downloadManager.isRunning || waitingForNetwork.get())
                    ) {
                        delay(1.seconds)
                    }
                } finally {
                    networkStatusJob.cancel()
                }
            }

            return Result.success()
        } finally {
            synchronized(workerLock) {
                if (activeWorkerId == workerId) {
                    if (downloadManager.isRunning && downloadManager.queueState.value.isNotEmpty()) {
                        val latestNetworkStatus = applicationContext.activeNetworkState()
                            .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi.get())
                        if (latestNetworkStatus == DownloadNetworkStatus.Available) {
                            downloadManager.downloaderPause()
                        } else {
                            pauseForNetwork(latestNetworkStatus)
                        }
                    }
                    activeWorkerId = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "Downloader"

        private val workerLock = Any()

        @Volatile
        private var activeWorkerId: UUID? = null

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadJob>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
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
