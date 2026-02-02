package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceSearchPagingSource(
    source: CatalogueSource,
    private val query: String,
    private val filters: FilterList,
) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query, filters)
    }
}

class SourcePopularPagingSource(source: CatalogueSource) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(source: CatalogueSource) : BaseSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseSourcePagingSource(
    protected val source: CatalogueSource,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : SourcePagingSource() {

    private val seenManga = hashSetOf<String>()
    private var lastLoadTime = 0L
    
    // Track highest page loaded for UI display
    private var highestPageLoaded = 0

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Manga> {
        val page = params.key ?: 1

        return try {
            // Apply page load delay if page > 1 and delay is configured
            if (page > 1 && Companion.pageLoadDelayMs > 0) {
                val timeSinceLastLoad = System.currentTimeMillis() - lastLoadTime
                val remainingDelay = Companion.pageLoadDelayMs - timeSinceLastLoad
                if (remainingDelay > 0) {
                    delay(remainingDelay)
                }
            }

            val startTime = System.currentTimeMillis()
            val mangasPage = withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }
            lastLoadTime = System.currentTimeMillis()
            
            // Update global page tracker when a new highest page is loaded
            if (page.toInt() > highestPageLoaded) {
                highestPageLoaded = page.toInt()
                _currentPage.value = highestPageLoaded
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

    companion object {
        // Page load delay in milliseconds (can be updated from UI preferences)
        var pageLoadDelayMs = 0L
        
        // Global current page state for UI display
        private val _currentPage = MutableStateFlow(1)
        val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
        
        // Initial page for jump-to-page feature
        private var _initialPageOverride = 1
        
        // Reset page counter (call when creating new pager)
        // Also resets the initial page override so future searches start from page 1
        fun resetPageCounter() {
            _initialPageOverride = 1
            _currentPage.value = 1
        }
        
        // Set initial page for next pager creation
        fun setInitialPage(page: Int) {
            _initialPageOverride = page
            _currentPage.value = page
        }
    }
}

class NoResultsException : Exception()
