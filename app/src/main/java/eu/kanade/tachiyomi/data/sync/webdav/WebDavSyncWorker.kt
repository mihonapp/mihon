package eu.kanade.tachiyomi.data.sync.webdav

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.WebDavSyncPreferences
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

class WebDavSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject
    private lateinit var syncManager: WebDavSyncManager

    @Inject
    private lateinit var syncPreferences: WebDavSyncPreferences

    @Inject
    private lateinit var notifier: WebDavSyncNotifier

    override suspend fun doWork(): Result {
        graph.inject(this)

        val isManual = inputData.getBoolean(IS_MANUAL_KEY, false)

        if (!isManual && (!syncPreferences.syncEnabled.get() || !syncPreferences.isConfigured())) {
            return Result.success()
        }

        if (BackupRestoreWorker.isRunning(context.workManager)) {
            return if (isManual) Result.failure() else Result.retry()
        }

        if (isManual) {
            setForegroundSafely()
        }

        return try {
            val summary = syncManager.sync()
            if (isManual || summary.missingExtensions.isNotEmpty()) {
                notifier.showComplete(summary)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "WebDAV sync failed" }
            if (isManual) {
                notifier.showError(e.message)
                Result.failure()
            } else {
                Result.retry()
            }
        } finally {
            context.cancelNotification(Notifications.ID_SYNC_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SYNC_PROGRESS,
            notifier.showProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_PERIODIC = "WebDavSync:periodic"
        private const val TAG_MANUAL = "WebDavSync:manual"
        private const val TAG_DEBOUNCED = "WebDavSync:debounced"
        private const val IS_MANUAL_KEY = "is_manual"
        private const val READER_EXIT_DEBOUNCE_SECONDS = 5L
        private const val APP_START_COOLDOWN_MS = 60_000L

        suspend fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL) ||
                context.workManager.isRunning(TAG_DEBOUNCED)
        }

        fun setupPeriodicTask(context: Context) {
            val prefs = context.appGraph.webDavSyncPreferences
            val enabled = prefs.syncEnabled.get() && prefs.isConfigured()
            val intervalMinutes = prefs.syncIntervalMinutes.get()

            if (enabled && intervalMinutes > 0) {
                val networkType = if (prefs.syncWifiOnly.get()) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()

                val request = PeriodicWorkRequestBuilder<WebDavSyncWorker>(
                    intervalMinutes.toLong().coerceAtLeast(15L),
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                    .addTag(TAG_PERIODIC)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(IS_MANUAL_KEY to false))
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    TAG_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(TAG_PERIODIC)
            }
        }

        fun startNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WebDavSyncWorker>()
                .addTag(TAG_MANUAL)
                .setInputData(workDataOf(IS_MANUAL_KEY to true))
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }

        fun enqueueDebounced(context: Context) {
            val prefs = context.appGraph.webDavSyncPreferences
            if (!prefs.syncEnabled.get() || !prefs.isConfigured()) return

            val networkType = if (prefs.syncWifiOnly.get()) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = OneTimeWorkRequestBuilder<WebDavSyncWorker>()
                .addTag(TAG_DEBOUNCED)
                .setConstraints(constraints)
                .setInitialDelay(READER_EXIT_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(IS_MANUAL_KEY to false))
                .build()

            context.workManager.enqueueUniqueWork(TAG_DEBOUNCED, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueueOnAppStartIfNeeded(context: Context) {
            val prefs = context.appGraph.webDavSyncPreferences
            if (!prefs.syncEnabled.get() || !prefs.syncOnAppStart.get() || !prefs.isConfigured()) return

            val lastSync = prefs.lastSyncTimestamp.get()
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - lastSync < APP_START_COOLDOWN_MS) return

            enqueueDebounced(context)
        }
    }
}
