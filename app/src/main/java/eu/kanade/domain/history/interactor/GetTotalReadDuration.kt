package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository

class GetTotalReadDuration(
    private val repository: HistoryRepository,
) {

    suspend fun await(): Long {
        return repository.getTotalReadDuration()
    }
}
