package mihon.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority
import mihon.sync.drive.GoogleDriveApi
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager

/**
 * What the synchronisation is costing in storage, on the device and on the account.
 *
 * Gathered on demand rather than kept up to date: it is a figure the user goes looking for once in
 * a while, and the remote half costs a couple of network round trips.
 */
@Inject
@SingleIn(AppScope::class)
class SyncUsage(
    private val driveApi: GoogleDriveApi,
    private val syncPreferences: SyncPreferences,
    private val storageManager: StorageManager,
) {

    data class Usage(
        val localBackupBytes: Long = 0,
        val localBackupFiles: Int = 0,
        val remoteBytes: Long = 0,
        val remoteFiles: Int = 0,
        val quotaUsedBytes: Long? = null,
        val quotaLimitBytes: Long? = null,
    ) {
        val hasQuota: Boolean get() = quotaUsedBytes != null && (quotaLimitBytes ?: 0) > 0
    }

    suspend fun read(): Usage = withIOContext {
        val (localBytes, localFiles) = localBackups()
        val (remoteBytes, remoteFiles) = remoteFolder()
        val quota = runCatching { driveApi.quota() }.getOrElse {
            logcat(LogPriority.WARN, it) { "Could not read the Drive quota" }
            null
        }

        Usage(
            localBackupBytes = localBytes,
            localBackupFiles = localFiles,
            remoteBytes = remoteBytes,
            remoteFiles = remoteFiles,
            quotaUsedBytes = quota?.usageBytes,
            quotaLimitBytes = quota?.limitBytes,
        )
    }

    /**
     * The automatic backups Mihon keeps on the device, including the one written before the first
     * sync that could delete anything.
     */
    private fun localBackups(): Pair<Long, Int> {
        val directory = runCatching { storageManager.getAutomaticBackupsDirectory() }.getOrNull() ?: return 0L to 0
        val files = runCatching { directory.listFiles() }.getOrNull().orEmpty()
        return files.sumOf { it.length() } to files.size
    }

    private suspend fun remoteFolder(): Pair<Long, Int> {
        val rootId = syncPreferences.rootFolderId().get()
        val libraryId = syncPreferences.libraryFolderId().get()
        if (rootId.isBlank() && libraryId.isBlank()) return 0L to 0

        return try {
            // listFolder leaves subfolders out, so the root and the library never count each other.
            val files = buildList {
                if (rootId.isNotBlank()) addAll(driveApi.listFolder(rootId))
                if (libraryId.isNotBlank()) addAll(driveApi.listFolder(libraryId))
            }
            files.sumOf { it.size.toLongOrNull() ?: 0L } to files.size
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not measure the sync folder" }
            0L to 0
        }
    }
}
