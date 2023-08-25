package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class BackupCreateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)

        if (isAutoBackup && BackupRestoreJob.isRunning(context)) return Result.retry()

        val backupPreferences = Injekt.get<BackupPreferences>()
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: backupPreferences.backupsDirectory().get().toUri()
        val flags = inputData.getInt(BACKUP_FLAGS_KEY, BackupConst.BACKUP_ALL)

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to run on foreground service" }
        }

        return try {
            val location = BackupManager(context).createBackup(uri, flags, isAutoBackup)
            if (!isAutoBackup) notifier.showBackupComplete(UniFile.fromUri(context, location.toUri()))
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            if (!isAutoBackup) notifier.showBackupError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
        )
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val backupPreferences = Injekt.get<BackupPreferences>()
            val interval = prefInterval ?: backupPreferences.backupInterval().get()
            if (interval > 0) {
                val constraints = Constraints(
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10.minutes.toJavaDuration())
                    .addTag(TAG_AUTO)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        fun startNow(context: Context, uri: Uri, flags: Int) {
            val inputData = workDataOf(
                IS_AUTO_BACKUP_KEY to false,
                LOCATION_URI_KEY to uri.toString(),
                BACKUP_FLAGS_KEY to flags,
            )
            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG_MANUAL)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}

private const val TAG_AUTO = "BackupCreator"
private const val TAG_MANUAL = "$TAG_AUTO:manual"

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup" // Boolean
private const val LOCATION_URI_KEY = "location_uri" // String
private const val BACKUP_FLAGS_KEY = "backup_flags" // Int
