package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.HistoryImpl

class RemoveHistoryById(
    private val repository: HistoryRepository
) {

    suspend fun await(history: History): Boolean {
        // Workaround for list not freaking out when changing reference varaible
        val history = HistoryImpl().apply {
            id = history.id
            chapter_id = history.chapter_id
            last_read = history.last_read
            time_read = history.time_read
        }
        return repository.resetHistory(history)
    }
}
