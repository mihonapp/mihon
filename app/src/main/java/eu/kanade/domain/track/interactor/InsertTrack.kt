package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.Track
import eu.kanade.domain.track.repository.TrackRepository
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class InsertTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun awaitAll(tracks: List<Track>) {
        try {
            trackRepository.insertAll(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
