package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toChapterUpdate
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.domain.track.model.Track
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SyncChaptersWithTrackServiceTwoWay(
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
) {

    suspend fun await(
        chapters: List<Chapter>,
        remoteTrack: Track,
        service: TrackService,
    ) {
        val sortedChapters = chapters.sortedBy { it.chapterNumber }
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
