package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.repository.TrackRepository
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class DeleteTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(mangaId: Long, syncId: Long) {
        try {
            trackRepository.delete(mangaId, syncId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
