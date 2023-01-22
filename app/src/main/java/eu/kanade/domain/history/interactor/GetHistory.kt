package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.HistoryWithRelations

class GetHistory(
    private val repository: HistoryRepository,
) {

    fun subscribe(query: String): Flow<List<HistoryWithRelations>> {
        return repository.getHistory(query)
    }
}
