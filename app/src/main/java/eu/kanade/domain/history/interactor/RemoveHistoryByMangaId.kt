package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository

class RemoveHistoryByMangaId(
    private val repository: HistoryRepository,
) {

    suspend fun await(mangaId: Long) {
        repository.resetHistoryByMangaId(mangaId)
    }
}
