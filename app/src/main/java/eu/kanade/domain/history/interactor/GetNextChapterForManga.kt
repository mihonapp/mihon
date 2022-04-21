package eu.kanade.domain.history.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.repository.HistoryRepository

class GetNextChapterForManga(
    private val repository: HistoryRepository
) {

    suspend fun await(mangaId: Long, chapterId: Long): Chapter? {
        return repository.getNextChapterForManga(mangaId, chapterId)
    }
}
