package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.CancellationException
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSource

class SourceSearchPagingSource(
    source: suspend () -> Source,
    private val query: String,
    private val filters: FilterList,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query, filters)
    }
}

class SourcePopularPagingSource(
    source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(
    source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseSourcePagingSource(
    private val source: suspend () -> Source,
    private val networkToLocalManga: NetworkToLocalManga,
) : SourcePagingSource() {

    private val seenManga = hashSetOf<String>()

    abstract suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Manga> {
        val page = params.key ?: 1

        return try {
            val source = source()
            val mangasPage = withIOContext {
                requestNextPage(source, page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            val manga = mangasPage.mangas
                .map { it.toDomainManga(source.id) }
                .filter { seenManga.add(it.url) }
                .let { networkToLocalManga(it) }

            LoadResult.Page(
                data = manga,
                prevKey = null,
                nextKey = if (mangasPage.hasNextPage) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Manga>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
