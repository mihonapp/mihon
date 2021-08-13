package eu.kanade.tachiyomi.data.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class UpdaterJob(private val context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork() = runBlocking {
        try {
            val result = AppUpdateChecker().checkForUpdate()

            if (result is AppUpdateResult.NewUpdate) {
                UpdaterNotifier(context).promptUpdate(result.release.getDownloadLink())
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"

        fun setupTask(context: Context) {
            // Never check for updates in debug builds that don't include the updater
            if (BuildConfig.DEBUG && !BuildConfig.INCLUDE_UPDATER) {
                cancelTask(context)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdaterJob>(
                7,
                TimeUnit.DAYS,
                3,
                TimeUnit.HOURS
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
        }

        fun cancelTask(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}
