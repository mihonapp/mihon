package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.model.HistoryUpdate
import eu.kanade.domain.history.repository.HistoryRepository

class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        historyRepository.upsertHistory(historyUpdate)
    }
}
