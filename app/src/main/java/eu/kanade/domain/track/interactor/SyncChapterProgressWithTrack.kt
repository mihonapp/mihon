package eu.kanade.domain.track.interactor

import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.PageTracker
import eu.kanade.tachiyomi.data.track.Tracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        if (tracker !is EnhancedTracker) {
            return
        }

        val sortedChapters = getChaptersByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { it.copy(read = true).toChapterUpdate() }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val updatedTrack = remoteTrack.copy(lastChapterRead = localLastRead.toDouble())

        val pageReadProgressUpdates = (tracker as? PageTracker)?.runCatching {
            sortedChapters.filter { it.chapterNumber > remoteTrack.lastChapterRead && !it.read }
                .map { it.toDbChapter() }
                .let { tracker.batchGetChapterProgress(it) }
                .mapNotNull { (chapter, page) ->
                    if (page >= 0) chapter.toDomainChapter()?.copy(lastPageRead = page.toLong())?.toChapterUpdate() else null
                }
        }?.getOrNull() ?: listOf()
        if (pageReadProgressUpdates.isNotEmpty()) logcat(LogPriority.ERROR) { pageReadProgressUpdates.toString() }
        try {
            tracker.update(updatedTrack.toDbTrack())
            updateChapter.awaitAll(chapterUpdates + pageReadProgressUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
