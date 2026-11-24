package eu.kanade.tachiyomi.ui.anilistwishlist.search

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.map
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class AnilistWishlistSearchViewModel(
    @Assisted private val sourceId: Long,
    @Assisted listingQuery: String?,
    @Assisted private val englishTitle: String?,
    @Assisted private val romajiTitle: String?,
    private val sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            sourceId: Long,
            listingQuery: String?,
            englishTitle: String?,
            romajiTitle: String?,
        ): AnilistWishlistSearchViewModel
    }

    var displayMode by sourcePreferences.sourceDisplayMode.asState(viewModelScope)

    private val initialSearchQueries = listOfNotNull(
        englishTitle?.trim()?.takeIf(String::isNotBlank),
        romajiTitle?.trim()?.takeIf(String::isNotBlank),
    ).distinct()

    val state: StateFlow<State>
        field = kotlinx.coroutines.flow.MutableStateFlow(
            State(
                listing = Listing.Search(
                    query = listingQuery,
                    filters = FilterList(),
                    searchQueries = initialSearchQueries,
                ),
            ),
        )

    private val source: Source? get() = state.value.source

    init {
        viewModelScope.launch {
            val loadedSource = sourceManager.getOrStub(sourceId)
            state.update {
                val filters = loadedSource.getFilterList()
                val listing = (it.listing as? Listing.Search)
                    ?.copy(filters = filters)
                    ?: it.listing

                it.copy(
                    source = loadedSource,
                    listing = listing,
                    filters = filters,
                    toolbarQuery = listing.query,
                )
            }
        }
    }

    val mangaPagerFlowFlow = state
        .map { it.source to it.listing }
        .filter { (loadedSource, _) -> loadedSource != null }
        .map { (loadedSource, listing) ->
            Pager(PagingConfig(pageSize = 25)) {
                WishlistSearchPagingSource(
                    source = loadedSource!!,
                    queries = (listing as Listing.Search).searchQueries,
                    filters = listing.filters,
                    networkToLocalManga = networkToLocalManga,
                )
            }.flow.map { pagingData ->
                pagingData.map { manga ->
                    getManga.subscribe(manga.url, manga.source)
                        .map { it ?: manga }
                        .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5.seconds),
                            manga,
                        )
                }
            }.cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns
        } else {
            libraryPreferences.portraitColumns
        }.get()

        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    fun search(query: String? = null, filters: FilterList? = null) {
        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(null, source?.getFilterList() ?: FilterList(), initialSearchQueries)

        val updatedQueries = if (query != null) {
            query.trim().takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList()
        } else {
            input.searchQueries
        }

        state.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                    searchQueries = updatedQueries,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun setToolbarQuery(query: String?) {
        state.update { it.copy(toolbarQuery = query) }
    }

    fun setFilters(filters: FilterList) {
        state.update { it.copy(filters = filters) }
    }

    fun resetFilters() {
        val loadedSource = source ?: return
        state.update { it.copy(filters = loadedSource.getFilterList()) }
    }

    fun openFilterSheet() {
        state.update { it.copy(isFilterDialogOpen = true) }
    }

    fun closeFilterSheet() {
        state.update { it.copy(isFilterDialogOpen = false) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data class Search(
            override val query: String?,
            override val filters: FilterList,
            val searchQueries: List<String>,
        ) : Listing(query, filters)
    }

    data class State(
        val listing: Listing,
        val source: Source? = null,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val isFilterDialogOpen: Boolean = false,
    )
}

private class WishlistSearchPagingSource(
    private val source: Source,
    private val queries: List<String>,
    private val filters: FilterList,
    private val networkToLocalManga: NetworkToLocalManga,
) : PagingSource<WishlistSearchPagingSource.SearchPageKey, Manga>() {

    data class SearchPageKey(
        val pages: List<Int?>,
    )

    override fun getRefreshKey(
        state: androidx.paging.PagingState<SearchPageKey, Manga>,
    ): SearchPageKey? = null

    override suspend fun load(
        params: LoadParams<SearchPageKey>,
    ): LoadResult<SearchPageKey, Manga> = withContext(Dispatchers.IO) {
        try {
            val currentPages = params.key?.pages ?: List(queries.size) { 1 }
            val nextPages = MutableList<Int?>(queries.size) { null }
            val results = mutableListOf<Manga>()

            queries.forEachIndexed { index, query ->
                val pageNumber = currentPages.getOrNull(index) ?: return@forEachIndexed

                val page = source.getSearchManga(
                    pageNumber,
                    query,
                    filters,
                )

                results += page.mangas.map { it.toDomainManga(source.id) }

                if (page.hasNextPage) {
                    nextPages[index] = pageNumber + 1
                }
            }

            LoadResult.Page(
                data = networkToLocalManga(results.distinctBy { it.url }),
                prevKey = null,
                nextKey = nextPages.takeIf { pages -> pages.any { it != null } }
                    ?.let(::SearchPageKey),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

