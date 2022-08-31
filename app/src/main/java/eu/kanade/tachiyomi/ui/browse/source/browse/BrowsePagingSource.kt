package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.withIOContext

abstract class BrowsePagingSource : PagingSource<Long, SManga>() {

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SManga> {
        val page = params.key ?: 1

        val mangasPage = try {
            withIOContext {
                requestNextPage(page.toInt())
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        return LoadResult.Page(
            data = mangasPage.mangas,
            prevKey = null,
            nextKey = if (mangasPage.hasNextPage) page + 1 else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Long, SManga>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}
