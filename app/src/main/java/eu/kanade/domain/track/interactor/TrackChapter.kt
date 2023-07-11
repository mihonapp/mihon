package eu.kanade.domain.track.interactor

import android.content.Context
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.DelayedTrackingUpdateJob
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.TrackManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack

class TrackChapter(
    private val getTracks: GetTracks,
    private val trackManager: TrackManager,
    private val insertTrack: InsertTrack,
    private val delayedTrackingStore: DelayedTrackingStore,
) {

    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double) = coroutineScope {
        launchNonCancellable {
            val tracks = getTracks.await(mangaId)

            if (tracks.isEmpty()) return@launchNonCancellable

            tracks.mapNotNull { track ->
                val service = trackManager.getService(track.syncId)
                if (service != null && service.isLogged && chapterNumber > track.lastChapterRead) {
                    val updatedTrack = track.copy(lastChapterRead = chapterNumber)

                    async {
                        runCatching {
                            try {
                                service.update(updatedTrack.toDbTrack(), true)
                                insertTrack.await(updatedTrack)
                            } catch (e: Exception) {
                                delayedTrackingStore.addItem(updatedTrack)
                                DelayedTrackingUpdateJob.setupTask(context)
                                throw e
                            }
                        }
                    }
                } else {
                    null
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }
}
