package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.domain.source.repository.SourcePagingSourceType

class SourceSearchPagingSource(source: CatalogueSource, val query: String, val filters: FilterList) :
    SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query, filters)
    }
}

class SourcePopularPagingSource(source: CatalogueSource) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(source: CatalogueSource) : SourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class SourcePagingSource(
    protected val source: CatalogueSource,
) : SourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SManga> {
        val page = params.key ?: 1

        val mangasPage = try {
            withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
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

class NoResultsException : Exception()
