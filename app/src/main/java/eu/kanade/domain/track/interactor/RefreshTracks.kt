package eu.kanade.domain.track.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithTrackServiceTwoWay
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.TrackManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack

class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackManager: TrackManager,
    private val insertTrack: InsertTrack,
    private val syncChaptersWithTrackServiceTwoWay: SyncChaptersWithTrackServiceTwoWay,
) {

    suspend fun await(mangaId: Long) {
        supervisorScope {
            getTracks.await(mangaId)
                .map { track ->
                    async {
                        val service = trackManager.getService(track.syncId)
                        if (service != null && service.isLoggedIn) {
                            try {
                                val updatedTrack = service.refresh(track.toDbTrack())
                                insertTrack.await(updatedTrack.toDomainTrack()!!)
                                syncChaptersWithTrackServiceTwoWay.await(mangaId, track, service)
                            } catch (e: Throwable) {
                                // Ignore errors and continue
                                logcat(LogPriority.ERROR, e)
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }
}
