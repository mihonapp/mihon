package eu.kanade.domain.history.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.repository.HistoryRepository

class GetNextChapter(
    private val repository: HistoryRepository,
) {

    suspend fun await(mangaId: Long, chapterId: Long): Chapter? {
        return repository.getNextChapter(mangaId, chapterId)
    }

    suspend fun await(): Chapter? {
        val history = repository.getLastHistory() ?: return null
        return repository.getNextChapter(history.mangaId, history.chapterId)
    }
}
