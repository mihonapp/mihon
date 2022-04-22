package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.domain.history.repository.HistoryRepository

class RemoveHistoryById(
    private val repository: HistoryRepository
) {

    suspend fun await(history: HistoryWithRelations) {
        repository.resetHistory(history.id)
    }
}
