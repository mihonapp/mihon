package eu.kanade.tachiyomi.data.backup.full

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.AbstractBackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.ValidatorParseException
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import okio.buffer
import okio.gzip
import okio.source

class FullBackupRestoreValidator : AbstractBackupRestoreValidator() {

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    override fun validate(context: Context, uri: Uri): Results {
        val backupManager = FullBackupManager(context)

        val backup = try {
            val backupString =
                context.contentResolver.openInputStream(uri)!!.source().gzip().buffer()
                    .use { it.readByteArray() }
            backupManager.parser.decodeFromByteArray(BackupSerializer, backupString)
        } catch (e: Exception) {
            throw ValidatorParseException(e)
        }

        if (backup.backupManga.isEmpty()) {
            throw Exception(context.getString(R.string.invalid_backup_file_missing_manga))
        }

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values
            .sorted()

        val trackers = backup.backupManga
            .flatMap { it.tracking }
            .map { it.syncId }
            .distinct()
        val missingTrackers = trackers
            .mapNotNull { trackManager.getService(it) }
            .filter { !it.isLogged }
            .map { context.getString(it.nameRes()) }
            .sorted()

        return Results(missingSources, missingTrackers)
    }
}
