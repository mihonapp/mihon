package eu.kanade.tachiyomi.data.backup.legacy

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.AbstractBackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.ValidatorParseException
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup
import kotlinx.serialization.json.decodeFromStream

class LegacyBackupRestoreValidator : AbstractBackupRestoreValidator() {

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if version or manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    override fun validate(context: Context, uri: Uri): Results {
        val backupManager = LegacyBackupManager(context)

        val backup = try {
            backupManager.parser.decodeFromStream<Backup>(
                context.contentResolver.openInputStream(uri)!!
            )
        } catch (e: Exception) {
            throw ValidatorParseException(e)
        }

        if (backup.version == null) {
            throw Exception(context.getString(R.string.invalid_backup_file_missing_data))
        }

        if (backup.mangas.isEmpty()) {
            throw Exception(context.getString(R.string.invalid_backup_file_missing_manga))
        }

        val sources = getSourceMapping(backup.extensions ?: emptyList())
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values
            .sorted()

        val trackers = backup.mangas
            .filterNot { it.track.isNullOrEmpty() }
            .flatMap { it.track ?: emptyList() }
            .map { it.sync_id }
            .distinct()
        val missingTrackers = trackers
            .mapNotNull { trackManager.getService(it) }
            .filter { !it.isLogged }
            .map { context.getString(it.nameRes()) }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    companion object {
        fun getSourceMapping(extensionsMapping: List<String>): Map<Long, String> {
            return extensionsMapping.associate {
                val items = it.split(":")
                items[0].toLong() to items[1]
            }
        }
    }
}
