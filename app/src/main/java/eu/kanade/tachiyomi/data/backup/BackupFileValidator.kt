package eu.kanade.tachiyomi.data.backup

import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
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
        val backupMangaFlow = backupDecoder.decodeManga(uri)
        val (_, backup) = backupDecoder.decodeMetadata(uri)

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values.map {
                val id = it.toLongOrNull()
                if (id == null) {
                    it
                } else {
                    sourceManager.getOrStub(id).toString()
                }
            }
            .distinct()
            .sorted()

        val trackers = mutableSetOf<Long>()
        backupMangaFlow.collect { manga ->
            manga.tracking.forEach { trackers += it.syncId.toLong() }
        }
        val missingTrackers = trackers
            .mapNotNull(trackerManager::get)
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )
}
