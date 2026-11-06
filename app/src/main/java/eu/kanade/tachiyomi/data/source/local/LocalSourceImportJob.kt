package eu.kanade.tachiyomi.data.source.local

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LocalSourceImportJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = LocalSourceImportNotifier(context)
    private val storageManager: StorageManager = Injekt.get()

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(URIS_KEY)?.map { it.toUri() } ?: return Result.failure()
        val folderName = inputData.getString(FOLDER_NAME_KEY) ?: return Result.failure()

        setForegroundSafely()

        return try {
            val localSourceDir = storageManager.getLocalSourceDirectory()
                ?: throw Exception("Storage not set")
            val mangaDir = localSourceDir.createDirectory(folderName)
                ?: throw Exception("Could not create directory")

            uris.forEachIndexed { index, uri ->
                val sourceFile = UniFile.fromUri(context, uri) ?: return@forEachIndexed
                val destFile = mangaDir.createFile(sourceFile.name) ?: return@forEachIndexed

                notifier.showImportProgress(index + 1, uris.size, folderName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            notifier.showImportComplete(folderName)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showImportError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_LOCAL_SOURCE_IMPORT_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val folderName = inputData.getString(FOLDER_NAME_KEY) ?: ""
        return ForegroundInfo(
            Notifications.ID_LOCAL_SOURCE_IMPORT_PROGRESS,
            notifier.showImportProgress(0, 100, folderName).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun start(context: Context, uris: List<Uri>, folderName: String) {
            val inputData = workDataOf(
                URIS_KEY to uris.map { it.toString() }.toTypedArray(),
                FOLDER_NAME_KEY to folderName,
            )
            val request = OneTimeWorkRequestBuilder<LocalSourceImportJob>()
                .addTag(TAG)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG + folderName, ExistingWorkPolicy.KEEP, request)
        }
    }
}

private const val TAG = "LocalSourceImport"
private const val URIS_KEY = "uris"
private const val FOLDER_NAME_KEY = "folder_name"
