package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository

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
