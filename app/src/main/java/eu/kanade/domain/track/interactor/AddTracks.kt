package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.source.Source
import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack

class AddTracks(
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
) {

    suspend fun bindEnhancedTracks(manga: Manga, source: Source) = withNonCancellableContext {
        getTracks.await(manga.id)
            .filterIsInstance<EnhancedTracker>()
            .filter { it.accept(source) }
            .forEach { service ->
                try {
                    service.match(manga)?.let { track ->
                        track.manga_id = manga.id
                        (service as Tracker).bind(track)
                        insertTrack.await(track.toDomainTrack()!!)

                        syncChapterProgressWithTrack.await(
                            manga.id,
                            track.toDomainTrack()!!,
                            service,
                        )
                    }
                } catch (e: Exception) {
                    logcat(
                        LogPriority.WARN,
                        e,
                    ) { "Could not match manga: ${manga.title} with service $service" }
                }
            }
    }
}
