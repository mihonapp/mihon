package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository

class DeleteHistoryTable(
    private val repository: HistoryRepository,
) {

    suspend fun await(): Boolean {
        return repository.deleteAllHistory()
    }
}
