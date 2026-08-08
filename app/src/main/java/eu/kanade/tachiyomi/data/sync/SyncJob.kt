package eu.kanade.tachiyomi.data.sync

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
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.SyncPreferences
import tachiyomi.domain.sync.service.SyncService
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val syncPreferences = Injekt.get<SyncPreferences>()
        if (SyncService.fromInt(syncPreferences.syncService.get()) == SyncService.NONE) return Result.failure()

        // Restoring or backing up at the same time would read a library that is mid-write
        if (BackupRestoreJob.isRunning(context) || BackupCreateJob.isManualJobRunning(context)) return Result.retry()

        setForegroundSafely()

        return try {
            SyncManager(context).sync()
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress(sync = true).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_AUTO) || context.workManager.isRunning(TAG_MANUAL)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = Injekt.get<SyncPreferences>()
            val interval = prefInterval ?: syncPreferences.syncInterval.get()

            if (interval > 0 && SyncService.fromInt(syncPreferences.syncService.get()) != SyncService.NONE) {
                val constraints = Constraints(
                    requiredNetworkType = NetworkType.CONNECTED,
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<SyncJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .addTag(TAG_AUTO)
                    .setConstraints(constraints)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        fun startNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncJob>()
                .addTag(TAG_MANUAL)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG_MANUAL)
        }
    }
}

private const val TAG_AUTO = "SyncJob"
private const val TAG_MANUAL = "$TAG_AUTO:manual"
