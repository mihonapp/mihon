package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga

class GetNextChapterForManga(
    private val repository: HistoryRepository
) {

    suspend fun await(manga: Manga, chapter: Chapter): Chapter? {
        return repository.getNextChapterForManga(manga, chapter)
    }
}
