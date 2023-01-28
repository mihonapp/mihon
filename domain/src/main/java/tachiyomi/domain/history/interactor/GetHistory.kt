package tachiyomi.domain.history.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository

class GetHistory(
    private val repository: HistoryRepository,
) {

    fun subscribe(query: String): Flow<List<HistoryWithRelations>> {
        return repository.getHistory(query)
    }
}
