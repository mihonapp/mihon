package eu.kanade.tachiyomi.data.manga

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.manga.interactor.MassImportNovels
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
 * DEPRECATED: Use eu.kanade.tachiyomi.data.massimport.MassImportJob instead.
 *
 * Worker for mass importing novels from URLs in the background.
 * Handles persistence across app restarts and displays notifications.
 *
 * This class has been superseded by the more comprehensive MassImportJob in the massimport package.
 */
@Deprecated(
    "Use eu.kanade.tachiyomi.data.massimport.MassImportJob instead",
    ReplaceWith("eu.kanade.tachiyomi.data.massimport.MassImportJob"),
)
class MassImportJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val massImportNovels: MassImportNovels = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
            setContentTitle("Importing novels...")
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
            val urls = inputData.getStringArray(KEY_URLS)?.toList() ?: emptyList()
            val addToLibrary = inputData.getBoolean(KEY_ADD_TO_LIBRARY, true)
            val categoryId = inputData.getLong(KEY_CATEGORY_ID, 0L).takeIf { it != 0L }
            val fetchDetails = inputData.getBoolean(KEY_FETCH_DETAILS, true)
            val fetchChapters = inputData.getBoolean(KEY_FETCH_CHAPTERS, false)

            if (urls.isEmpty()) {
                return Result.success()
            }

            setForegroundSafely()

            massImportNovels.startImport(
                urls = urls,
                addToLibrary = addToLibrary,
                categoryId = categoryId,
                fetchDetails = fetchDetails,
                fetchChapters = fetchChapters,
            )

            // Collect progress updates and update notifications
            massImportNovels.progress.collectLatest { progress ->
                progress?.let {
                    val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
                        setContentTitle("Importing novels...")
                        setProgress(progress.total, progress.current, false)
                        setSmallIcon(android.R.drawable.stat_sys_download)
                        setOngoing(true)
                        setOnlyAlertOnce(true)
                    }.build()
                }
            }

            // After completion, get the result
            val result = massImportNovels.result.value

            if (result != null) {
                showCompletionNotification(result)
            }

            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Mass import job failed" }
            Result.retry()
        }
    }

    private fun showCompletionNotification(result: MassImportNovels.ImportResult) {
        val message = "Imported: ${result.added.size}, Errors: ${result.errored.size}"

        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_MASS_IMPORT) {
            setContentTitle("Import Complete")
            setContentText(message)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setAutoCancel(true)
        }.build()
    }

    companion object {
        private const val TAG = "MassImportJob"
        private const val KEY_URLS = "urls"
        private const val KEY_ADD_TO_LIBRARY = "add_to_library"
        private const val KEY_CATEGORY_ID = "category_id"
        private const val KEY_FETCH_DETAILS = "fetch_details"
        private const val KEY_FETCH_CHAPTERS = "fetch_chapters"

        fun start(
            context: Context,
            urls: List<String>,
            addToLibrary: Boolean = true,
            categoryId: Long? = null,
            fetchDetails: Boolean = true,
            fetchChapters: Boolean = false,
        ) {
            val data = workDataOf(
                KEY_URLS to urls.toTypedArray(),
                KEY_ADD_TO_LIBRARY to addToLibrary,
                KEY_CATEGORY_ID to (categoryId ?: 0L),
                KEY_FETCH_DETAILS to fetchDetails,
                KEY_FETCH_CHAPTERS to fetchChapters,
            )

            val request = OneTimeWorkRequestBuilder<MassImportJob>()
                .setInputData(data)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
