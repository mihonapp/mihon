package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.model.HistoryWithRelations

class RemoveHistory(
    private val repository: HistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllHistory()
    }

    suspend fun await(history: HistoryWithRelations) {
        repository.resetHistory(history.id)
    }

    suspend fun await(mangaId: Long) {
        repository.resetHistoryByMangaId(mangaId)
    }
}
