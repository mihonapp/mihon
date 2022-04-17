package eu.kanade.domain.history.interactor

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import eu.kanade.data.history.local.HistoryPagingSource
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import kotlinx.coroutines.flow.Flow

class GetHistory(
    private val repository: HistoryRepository
) {

    fun subscribe(query: String): Flow<PagingData<MangaChapterHistory>> {
        return Pager(
            PagingConfig(pageSize = HistoryPagingSource.PAGE_SIZE)
        ) {
            repository.getHistory(query)
        }.flow
    }
}
