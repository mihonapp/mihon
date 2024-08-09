package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.Chapter


/**
 *
 */
interface PageTracker {

    data class ChapterReadProgress(
        val completed: Boolean,
        val page: Int
    ) {
        operator fun compareTo(b: ChapterReadProgress): Int =
            if (completed == b.completed)  page.coerceAtLeast(0) - b.page.coerceAtLeast(0)
            else completed.compareTo(b.completed)

    }

    suspend fun updatePageProgress(track: tachiyomi.domain.track.model.Track, page: Int) {}
    suspend fun updatePageProgressWithUrl(chapterUrl:String, page: Int) {}

    suspend fun batchUpdateRemoteProgress(chapters: List<Chapter>)

    suspend fun getChapterProgress(chapter: Chapter): ChapterReadProgress
    suspend fun batchGetChapterProgress(chapters: List<Chapter>): Map<Chapter, ChapterReadProgress> {
        return chapters.associateWith { getChapterProgress(it) }
    }
}
