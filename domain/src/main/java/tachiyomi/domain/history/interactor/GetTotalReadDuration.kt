package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.HistoryRepository

class GetTotalReadDuration(
    private val repository: HistoryRepository,
) {

    suspend fun await(): Long {
        return repository.getTotalReadDuration()
    }
}
