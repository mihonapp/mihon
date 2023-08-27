package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapterByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertTrack,
    private val getChapterByMangaId: GetChapterByMangaId,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        service: TrackService,
    ) {
        if (service !is EnhancedTrackService) {
            return
        }

        val sortedChapters = getChapterByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { it.copy(read = true).toChapterUpdate() }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val updatedTrack = remoteTrack.copy(lastChapterRead = localLastRead.toDouble())

        try {
            service.update(updatedTrack.toDbTrack())
            updateChapter.awaitAll(chapterUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
