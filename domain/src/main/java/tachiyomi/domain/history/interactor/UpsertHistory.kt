package tachiyomi.domain.history.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.repository.HistoryRepository

class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        try {
            historyRepository.upsertHistory(historyUpdate)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)

        }
    }

    suspend fun awaitAll(historyUpdate: List<HistoryUpdate>) {
        try {
            historyRepository.upsertAllHistory(historyUpdate)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)

        }
    }
}
