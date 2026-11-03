package eu.kanade.tachiyomi.data.backup

import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.source.service.SourceManager

@Inject
class BackupFileValidator(
    private val sourceManager: SourceManager,
    private val trackerManager: TrackerManager,
    private val backupDecoder: BackupDecoder,
) {

    /**
     * Checks for critical backup file data.
     *
     * @return List of missing sources or missing trackers.
     */
    suspend fun validate(uri: Uri): Results {
        val trackerIds = mutableSetOf<Int>()
        val backup = try {
            backupDecoder.decodeMetadata(uri, TrackedManga.serializer()) { manga ->
                manga.tracking.mapTo(trackerIds) { it.syncId }
            }.backup
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filterKeys { sourceManager.get(it) == null }
            .map { (id, name) ->
                // Some backups store the id as the name, and sources without a stub were backed up with no name
                if (name.isBlank() || name.toLongOrNull() != null) {
                    sourceManager.getOrStub(id).toString()
                } else {
                    name
                }
            }
            .distinct()
            .sorted()

        val missingTrackers = trackerIds
            .mapNotNull { trackerManager.get(it.toLong()) }
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )

    // Only the field validation reads, so decoding skips the rest of each entry
    @Serializable
    private class TrackedManga(
        @ProtoNumber(18) val tracking: List<BackupTracking> = emptyList(),
    )
}
