package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.repository.HistoryRepository

class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        historyRepository.upsertHistory(historyUpdate)
    }
}
