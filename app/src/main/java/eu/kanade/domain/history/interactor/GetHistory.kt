package eu.kanade.domain.history.interactor

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.domain.history.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

class GetHistory(
    private val repository: HistoryRepository,
) {

    fun subscribe(query: String): Flow<PagingData<HistoryWithRelations>> {
        return Pager(
            PagingConfig(pageSize = 25),
        ) {
            repository.getHistory(query)
        }.flow
    }
}
