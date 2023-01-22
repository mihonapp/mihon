package eu.kanade.domain.chapter.interactor

import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

class UpdateChapter(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapterUpdate: ChapterUpdate) {
        try {
            chapterRepository.update(chapterUpdate)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(chapterUpdates: List<ChapterUpdate>) {
        try {
            chapterRepository.updateAll(chapterUpdates)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
