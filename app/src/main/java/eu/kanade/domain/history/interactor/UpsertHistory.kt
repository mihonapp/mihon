package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.model.HistoryUpdate

class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        historyRepository.upsertHistory(historyUpdate)
    }
}
