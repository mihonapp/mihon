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
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class TranslationJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val processor: TranslationQueueProcessor = Injekt.get()
    private val repository: TranslationRepository = Injekt.get()
    private val setupValidator: TranslationSetupValidator = Injekt.get()
    private val notifier: TranslationNotifier = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_TRANSLATION_PROGRESS,
            notifier.foregroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        val workKind = TranslationWorkKind.from(inputData.getString(KEY_WORK_KIND))
        val laneId = inputData.getInt(KEY_LANE_ID, 0)
        if (repository.getPendingJobs(workKind).isEmpty()) {
            return Result.success()
        }
        val setup = setupValidator.readiness()
        if (!setup.ready) {
            repository.pausePendingJobsForSetup(workKind, setup.message)
            return Result.success()
        }

        setForegroundSafely()

        return when (processor.processPending(workKind, laneId)) {
            TranslationProcessResult.RetryLater -> Result.retry()
            TranslationProcessResult.Idle,
            TranslationProcessResult.Completed,
            TranslationProcessResult.Paused,
            -> Result.success()
        }
    }

    companion object {
        private const val TAG = "TranslationQueue"
        private const val NORMAL_WORK_NAME = "TranslationQueue"
        private const val RETRY_WORK_PREFIX = "TranslationQueueRetry"
        private const val KEY_WORK_KIND = "translation_work_kind"
        private const val KEY_LANE_ID = "translation_lane_id"

        fun start(
            context: Context,
            policy: TranslationWorkStartPolicy = TranslationWorkStartPolicy.Keep,
            reason: String = "start requested",
            kind: TranslationWorkKind = TranslationWorkKind.Normal,
            laneId: Int = 0,
        ) {
            val appContext = context.applicationContext
            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        KEY_WORK_KIND to kind.value,
                        KEY_LANE_ID to laneId,
                    ),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val existingWorkPolicy = when (policy) {
                TranslationWorkStartPolicy.Keep -> ExistingWorkPolicy.KEEP
                TranslationWorkStartPolicy.Replace -> ExistingWorkPolicy.REPLACE
            }
            runCatching {
                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(workName(kind, laneId), existingWorkPolicy, request)
            }.onSuccess {
                launchIO {
                    Injekt.get<TranslationRepository>().insertLog(
                        jobId = null,
                        pageId = null,
                        level = TranslationLogLevel.Debug,
                        tag = "queue",
                        message = "Translation worker start requested",
                        details = TranslationLogDetailsFormatter.queueState(
                            action = "worker_start",
                            jobId = null,
                            previousStatus = null,
                            nextStatus = null,
                            reason = reason,
                            extra = mapOf(
                                "policy" to policy.name,
                                "worker_kind" to kind.value,
                                "lane_id" to laneId,
                            ),
                        ),
                    )
                }
            }.onFailure { error ->
                launchIO {
                    Injekt.get<TranslationRepository>().insertLog(
                        jobId = null,
                        pageId = null,
                        level = TranslationLogLevel.Error,
                        tag = "queue",
                        message = "Failed to start translation worker",
                        details = TranslationLogDetailsFormatter.queueState(
                            action = "worker_start_failed",
                            jobId = null,
                            previousStatus = null,
                            nextStatus = null,
                            reason = error.message ?: error::class.simpleName.orEmpty(),
                            extra = mapOf(
                                "policy" to policy.name,
                                "worker_kind" to kind.value,
                                "lane_id" to laneId,
                            ),
                        ),
                    )
                }
            }
        }

        fun startManualRetryWorkers(
            context: Context,
            workerCount: Int,
            reason: String,
        ) {
            repeat(workerCount.coerceAtLeast(0)) { index ->
                start(
                    context = context,
                    policy = TranslationWorkStartPolicy.Keep,
                    reason = reason,
                    kind = TranslationWorkKind.ManualRetry,
                    laneId = index + 1,
                )
            }
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        /**
         * Blocking WorkManager state check. Call off the main thread.
         */
        @Deprecated(
            message = "Blocking; use isRunningFlow() instead",
            replaceWith = ReplaceWith("TranslationJob.isRunningFlow(context)"),
        )
        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosByTag(TAG)
                .get()
                .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(TAG)
                .asFlow()
                .map { list -> list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        }

        private fun workName(kind: TranslationWorkKind, laneId: Int): String {
            return when (kind) {
                TranslationWorkKind.Normal -> NORMAL_WORK_NAME
                TranslationWorkKind.ManualRetry -> "$RETRY_WORK_PREFIX-${laneId.coerceAtLeast(1)}"
            }
        }
    }
}
