package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.collectLatest
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Worker for translating chapters in the background.
 * Handles persistence across app restarts and displays notifications.
 */
class TranslationJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val translationService: TranslationService = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
            setContentTitle("Translating chapters...")
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
        }.build()
        return ForegroundInfo(
            Notifications.ID_MASS_IMPORT_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        return try {
            val chapterId = inputData.getLong(KEY_CHAPTER_ID, -1L)
            val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)

            if (chapterId == -1L || mangaId == -1L) {
                return Result.success()
            }

            setForegroundSafely()

            // The TranslationService will start processing the queue
            translationService.start()

            // Collect progress updates and update notifications
            translationService.progressState.collectLatest { progress ->
                if (progress.isRunning) {
                    val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
                        setContentTitle("Translating chapters...")
                        setContentText(progress.currentChapterName ?: "Processing...")
                        setProgress(progress.totalChapters, progress.completedChapters, false)
                        setSmallIcon(android.R.drawable.stat_sys_download)
                        setOngoing(true)
                        setOnlyAlertOnce(true)
                    }.build()
                }
            }

            showCompletionNotification()
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Translation job failed" }
            Result.retry()
        }
    }

    private fun showCompletionNotification() {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
            setContentTitle("Translation Complete")
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setAutoCancel(true)
        }.build()
    }

    companion object {
        private const val TAG = "TranslationJob"
        private const val KEY_CHAPTER_ID = "chapter_id"
        private const val KEY_MANGA_ID = "manga_id"

        fun start(context: Context, chapterId: Long = -1L, mangaId: Long = -1L) {
            val data = workDataOf(
                KEY_CHAPTER_ID to chapterId,
                KEY_MANGA_ID to mangaId,
            )

            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .setInputData(data)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
