package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.Track
import eu.kanade.domain.track.repository.TrackRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class GetTracks(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(mangaId: Long): List<Track> {
        return try {
            trackRepository.getTracksByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    fun subscribe(mangaId: Long): Flow<List<Track>> {
        return trackRepository.getTracksByMangaIdAsFlow(mangaId)
    }
}
