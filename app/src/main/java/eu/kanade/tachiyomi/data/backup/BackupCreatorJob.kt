package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.full.FullBackupManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.notificationManager
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class BackupCreatorJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<PreferencesHelper>()
        val notifier = BackupNotifier(context)
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: preferences.backupsDirectory().get().toUri()
        val flags = inputData.getInt(BACKUP_FLAGS_KEY, BackupConst.BACKUP_ALL)
        val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)

        context.notificationManager.notify(Notifications.ID_BACKUP_PROGRESS, notifier.showBackupProgress().build())
        return try {
            val location = FullBackupManager(context).createBackup(uri, flags, isAutoBackup)
            if (!isAutoBackup) notifier.showBackupComplete(UniFile.fromUri(context, location.toUri()))
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            if (!isAutoBackup) notifier.showBackupError(e.message)
            Result.failure()
        } finally {
            context.notificationManager.cancel(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean {
            val list = WorkManager.getInstance(context).getWorkInfosByTag(TAG_MANUAL).get()
            return list.find { it.state == WorkInfo.State.RUNNING } != null
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = Injekt.get<PreferencesHelper>()
            val interval = prefInterval ?: preferences.backupInterval().get()
            val workManager = WorkManager.getInstance(context)
            if (interval > 0) {
                val request = PeriodicWorkRequestBuilder<BackupCreatorJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG_AUTO)
                    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                    .build()

                workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.REPLACE, request)
            } else {
                workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        fun startNow(context: Context, uri: Uri, flags: Int) {
            val inputData = workDataOf(
                IS_AUTO_BACKUP_KEY to false,
                LOCATION_URI_KEY to uri.toString(),
                BACKUP_FLAGS_KEY to flags,
            )
            val request = OneTimeWorkRequestBuilder<BackupCreatorJob>()
                .addTag(TAG_MANUAL)
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}

private const val TAG_AUTO = "BackupCreator"
private const val TAG_MANUAL = "$TAG_AUTO:manual"

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup" // Boolean
private const val LOCATION_URI_KEY = "location_uri" // String
private const val BACKUP_FLAGS_KEY = "backup_flags" // Int
