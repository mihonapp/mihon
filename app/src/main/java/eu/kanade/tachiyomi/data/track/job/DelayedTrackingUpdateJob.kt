package eu.kanade.tachiyomi.data.track.job

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class DelayedTrackingUpdateJob(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = Injekt.get<DatabaseHelper>()
        val trackManager = Injekt.get<TrackManager>()
        val delayedTrackingStore = Injekt.get<DelayedTrackingStore>()

        withContext(Dispatchers.IO) {
            val tracks = delayedTrackingStore.getItems().mapNotNull {
                val manga = db.getManga(it.mangaId).executeAsBlocking() ?: return@withContext
                db.getTracks(manga).executeAsBlocking()
                    .find { track -> track.id == it.trackId }
                    ?.also { track ->
                        track.last_chapter_read = it.lastChapterRead
                    }
            }

            tracks.forEach { track ->
                try {
                    val service = trackManager.getService(track.sync_id)
                    if (service != null && service.isLogged) {
                        service.update(track, true)
                        db.insertTrack(track).executeAsBlocking()
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }

            delayedTrackingStore.clear()
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"

        fun setupTask(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DelayedTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
