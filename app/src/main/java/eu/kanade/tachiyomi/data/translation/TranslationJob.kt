package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class TranslationJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val processor: TranslationQueueProcessor = Injekt.get()
    private val repository: TranslationRepository = Injekt.get()
    private val preferences: TranslationPreferences = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(applicationContext.stringResource(MR.strings.label_translation_queue))
            setSmallIcon(android.R.drawable.stat_sys_upload)
            setOngoing(true)
        }.build()
        return ForegroundInfo(
            Notifications.ID_TRANSLATION_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        if (repository.getPendingJobs().isEmpty()) {
            return Result.success()
        }
        if (preferences.geminiApiKey.get().isBlank()) {
            repository.getPendingJobs().forEach { job ->
                repository.updateJobStatus(
                    job = job,
                    status = TranslationJobStatus.PausedAuth,
                    errorMessage = "Gemini API key is empty",
                )
                repository.insertLog(
                    jobId = job._id,
                    pageId = null,
                    level = TranslationLogLevel.Error,
                    tag = "queue",
                    message = "Paused translation queue",
                    details = "Gemini API key is empty",
                )
            }
            return Result.success()
        }

        setForegroundSafely()

        return when (processor.processPending()) {
            TranslationProcessResult.RetryLater -> Result.retry()
            TranslationProcessResult.Idle,
            TranslationProcessResult.Completed,
            TranslationProcessResult.Paused,
            -> Result.success()
        }
    }

    companion object {
        private const val TAG = "TranslationQueue"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        }
    }
}
