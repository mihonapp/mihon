package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.Chapter


/**
 *
 */
interface PageTracker {
    suspend fun update(track: tachiyomi.domain.track.model.Track, page: Int) {}
    suspend fun updateWithUrl(chapterUrl:String, page: Int)

    /**
     * If completed it would be 0, since complete status should already be handled by EnhancedTrackers
     */
    suspend fun getChapterProgress(chapter: Chapter): Int
    suspend fun batchGetChapterProgress(chapters: List<Chapter>): Map<Chapter, Int> {
        return chapters.associateWith { getChapterProgress(it) }
    }
}
