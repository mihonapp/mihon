package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.interactor.GetTracks

@Inject
class SyncLibraryTracks(
    private val getLibraryManga: GetLibraryManga,
    private val getTracks: GetTracks,
    private val refreshTracks: RefreshTracks,
    private val trackerManager: TrackerManager,
) {

    suspend fun await(): Int {
        val loggedInTrackers = trackerManager.loggedInTrackers()
        if (loggedInTrackers.isEmpty()) return 0

        val libraryManga = getLibraryManga.await()
        var updatedCount = 0

        supervisorScope {
            libraryManga.map { libraryEntry ->
                async(Dispatchers.IO) {
                    try {
                        val tracks = getTracks.await(libraryEntry.manga.id)
                        if (tracks.any { track -> loggedInTrackers.any { it.id == track.trackerId } }) {
                            val errors = refreshTracks.await(libraryEntry.manga.id)
                            if (errors.isEmpty()) {
                                updatedCount++
                            }
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.WARN, e) { "Failed to sync tracks for ${libraryEntry.manga.title}" }
                    }
                }
            }.awaitAll()
        }

        return updatedCount
    }
}
