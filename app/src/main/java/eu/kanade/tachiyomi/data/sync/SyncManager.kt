package eu.kanade.tachiyomi.data.sync

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveApi
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.domain.sync.service.SyncPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock

/**
 * Pulls the remote snapshot, merges it with the local library, pushes the result back and applies
 * it locally, so every device converges on the same state.
 */
class SyncManager(
    private val context: Context,
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private val parser: ProtoBuf = Injekt.get(),
    private val driveApi: GoogleDriveApi = GoogleDriveApi(),
) {

    suspend fun sync() {
        val local = BackupCreator(context, isAutoBackup = false).createBackup(SYNC_OPTIONS)
        val remote = driveApi.downloadSnapshot()?.let { decode(it) }

        val merged = if (remote == null) local else SyncMerger.merge(local, remote)

        driveApi.uploadSnapshot(encode(merged))

        // Nothing to apply when this device is the only source of data yet
        if (remote != null) {
            applyLocally(merged)
        }

        syncPreferences.lastSyncTimestamp.set(Clock.System.now().toEpochMilliseconds())
    }

    private fun decode(content: ByteArray): Backup {
        val file = context.createFileInCacheDir(REMOTE_SNAPSHOT_FILENAME)
        file.writeBytes(content)
        return BackupDecoder(context, parser).decode(file.toUri())
    }

    private fun encode(backup: Backup): ByteArray {
        val file = context.createFileInCacheDir(UPLOAD_SNAPSHOT_FILENAME)
        file.sink().gzip().buffer().use { it.write(parser.encodeToByteArray(Backup.serializer(), backup)) }
        return file.readBytes()
    }

    /**
     * The restorer only reads from a file, so the merged snapshot takes a detour through the cache
     * directory rather than duplicating the whole restore logic for an in-memory backup.
     */
    private suspend fun applyLocally(backup: Backup) {
        val file = context.createFileInCacheDir(MERGED_SNAPSHOT_FILENAME)
        file.sink().gzip().buffer().use { it.write(parser.encodeToByteArray(Backup.serializer(), backup)) }

        BackupRestorer(context, BackupNotifier(context), isSync = true)
            .restore(file.toUri(), RESTORE_OPTIONS)

        file.delete()
    }

    companion object {
        // App and source settings are deliberately left out: they describe this device, not the library
        private val SYNC_OPTIONS = BackupOptions(
            libraryEntries = true,
            categories = true,
            chapters = true,
            tracking = true,
            history = true,
            readEntries = true,
            appSettings = false,
            extensionStores = true,
            sourceSettings = false,
            privateSettings = false,
        )

        private val RESTORE_OPTIONS = RestoreOptions(
            libraryEntries = true,
            categories = true,
            appSettings = false,
            extensionStores = true,
            sourceSettings = false,
        )

        private const val REMOTE_SNAPSHOT_FILENAME = "sync_remote.tachibk"
        private const val UPLOAD_SNAPSHOT_FILENAME = "sync_upload.tachibk"
        private const val MERGED_SNAPSHOT_FILENAME = "sync_merged.tachibk"
    }
}
