package eu.kanade.data.history.local

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import logcat.logcat

class HistoryPagingSource(
    private val repository: HistoryRepository,
    private val query: String
) : PagingSource<Int, MangaChapterHistory>() {

    override fun getRefreshKey(state: PagingState<Int, MangaChapterHistory>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult.Page<Int, MangaChapterHistory> {
        val nextPageNumber = params.key ?: 0
        logcat { "Loading page $nextPageNumber" }

        val response = repository.getHistory(PAGE_SIZE, nextPageNumber, query)

        val nextKey = if (response.size == 25) {
            nextPageNumber + 1
        } else {
            null
        }

        return LoadResult.Page(
            data = response,
            prevKey = null,
            nextKey = nextKey
        )
    }

    companion object {
        const val PAGE_SIZE = 25
    }
}
