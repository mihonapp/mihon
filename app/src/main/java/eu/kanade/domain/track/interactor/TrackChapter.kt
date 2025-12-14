package eu.kanade.domain.track.interactor

import android.content.Context
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.DelayedTrackingUpdateJob
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackChapter(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val delayedTrackingStore: DelayedTrackingStore,
    private val trackPreferences: TrackPreferences,
) {

    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {
        withNonCancellableContext {
            var tracks = getTracks.await(mangaId)
            
            // If no tracks exist and auto-bind is enabled, try to auto-bind enhanced trackers
            if (tracks.isEmpty() && trackPreferences.autoBindEnhancedTrackers().get()) {
                tryAutoBindEnhancedTrackers(mangaId)
                tracks = getTracks.await(mangaId)
            }
            
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        try {
                            val updatedTrack = service.refresh(track.toDbTrack())
                                .toDomainTrack(idRequired = true)!!
                                .copy(lastChapterRead = chapterNumber)
                            service.update(updatedTrack.toDbTrack(), true)
                            insertTrack.await(updatedTrack)
                            delayedTrackingStore.remove(track.id)
                        } catch (e: Exception) {
                            delayedTrackingStore.add(track.id, chapterNumber)
                            if (setupJobOnFailure) {
                                DelayedTrackingUpdateJob.setupTask(context)
                            }
                            throw e
                        }
                    }
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.WARN, it) }
        }
    }
    
    private suspend fun tryAutoBindEnhancedTrackers(mangaId: Long) {
        try {
            val getManga = Injekt.get<GetManga>()
            val sourceManager = Injekt.get<SourceManager>()
            
            val manga = getManga.await(mangaId) ?: return
            val source = sourceManager.getOrStub(manga.source)
            
            trackerManager.loggedInTrackers()
                .filterIsInstance<EnhancedTracker>()
                .filter { it.accept(source) }
                .forEach { enhancedTracker ->
                    try {
                        enhancedTracker.match(manga)?.let { trackSearch ->
                            trackSearch.manga_id = manga.id
                            (enhancedTracker as Tracker).bind(trackSearch)
                            insertTrack.await(trackSearch.toDomainTrack(idRequired = false)!!)
                            
                            logcat(LogPriority.DEBUG) {
                                "Auto-bound enhanced tracker ${(enhancedTracker as Tracker).name} for manga: ${manga.title}"
                            }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) {
                            "Failed to auto-bind enhanced tracker ${(enhancedTracker as Tracker).name} for manga: ${manga.title}"
                        }
                    }
                }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to auto-bind enhanced trackers for manga ID: $mangaId" }
        }
    }
}
