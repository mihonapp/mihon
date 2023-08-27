package eu.kanade.domain.track.interactor

import eu.kanade.domain.chapter.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack

class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackManager: TrackManager,
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     *
     * @return Failed updates.
     */
    suspend fun await(mangaId: Long): List<Pair<TrackService?, Throwable>> {
        return supervisorScope {
            return@supervisorScope getTracks.await(mangaId)
                .map { track ->
                    async {
                        val service = trackManager.getService(track.syncId)
                        return@async try {
                            if (service?.isLoggedIn == true) {
                                val updatedTrack = service.refresh(track.toDbTrack())
                                insertTrack.await(updatedTrack.toDomainTrack()!!)
                                syncChapterProgressWithTrack.await(mangaId, track, service)
                            }
                            null
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }
}
