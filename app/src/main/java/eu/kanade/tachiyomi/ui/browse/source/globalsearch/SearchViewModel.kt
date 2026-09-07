package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart.QueryNormalizer
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.smart.SmartSearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.Executors

abstract class SearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    private val preferences: SourcePreferences,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(initialState)

    // Subclasses can't touch the backing field (Kotlin forbids a visibility modifier on one),
    // so state writes from them go through here.
    protected fun updateState(function: (State) -> State) {
        state.update(function)
    }

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    protected val pinnedSources = sourcePreferences.pinnedSources.get()

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    /**
     * Orders per-source results in [State.items]. Migration search overrides this to sort by
     * source priority; global search keeps the default (pinned first, then alphabetical).
     */
    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    // Live-search: when the user keeps typing, debounce and re-run search automatically
    // without requiring them to press search/enter. Only enabled for global search.
    protected var liveSearchEnabled: Boolean = false
    private var liveSearchJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { onlyShowHasResults ->
                state.update { it.copy(onlyShowHasResults = onlyShowHasResults) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open suspend fun getEnabledSources(): List<Source> {
        return sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private suspend fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.getInstalledExtensions()
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        state.update { it.copy(searchQuery = query) }
        if (liveSearchEnabled) {
            scheduleLiveSearch()
        }
    }

    /**
     * Debounced live search: as soon as the user stops typing (~400ms), search automatically.
     */
    private fun scheduleLiveSearch() {
        liveSearchJob?.cancel()
        val query = state.value.searchQuery?.trim().orEmpty()
        liveSearchJob = viewModelScope.launch {
            delay(400)
            if (query.isNotBlank()) search()
        }
    }

    fun setSourceFilter(filter: SourceFilter) {
        state.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        searchJob = viewModelScope.launchIO {
            val sources = getSelectedSources()

            // Reuse previous per-source results if re-running the same query under a new filter.
            val previousItems = state.value.items

            updateState {
                it.copy(
                    isSearching = true,
                    completedSources = 0,
                    totalSources = sources.size,
                    items = if (sameQuery) previousItems else sources.associateWith { SearchItemResult.Loading },
                    results = emptyList(),
                    suggestions = emptyList(),
                )
            }

            if (sources.isEmpty()) {
                updateState { it.copy(isSearching = false) }
                return@launchIO
            }

            // Each source pushes its raw hits when it finishes so results stream in
            // incrementally instead of waiting for every source to complete.
            val resultChannel = Channel<Pair<Source, List<Manga>>>(Channel.UNLIMITED)

            sources.forEach { source ->
                async {
                    val titles = runCatching {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query, source.getFilterList())
                        }
                        page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }
                    }.getOrDefault(emptyList())

                    if (isActive) resultChannel.send(source to titles)
                }
            }

            // Consume results as they arrive and update state incrementally.
            val accumulator = mutableListOf<Manga>()
            repeat(sources.size) {
                val (source, titles) = resultChannel.receive()
                if (isActive) {
                    accumulator.addAll(titles)
                    val result: SearchItemResult =
                        if (titles.isEmpty()) SearchItemResult.Success(emptyList())
                        else SearchItemResult.Success(titles)

                    updateState {
                        val updatedItems = (it.items + (source to result))
                            .toSortedMap(sortComparator(it.items + (source to result)))
                        it.copy(
                            completedSources = it.completedSources + 1,
                            items = updatedItems,
                            // Smart merge recomputed from all accumulated raw hits.
                            results = SmartSearchEngine.merge(QueryNormalizer.normalize(query), accumulator),
                        )
                    }
                }
            }

            // "Did you mean" suggestions when nothing matched across all sources.
            val finalResults = state.value.results
            val suggestions = if (finalResults.isEmpty()) {
                SmartSearchEngine.suggest(QueryNormalizer.normalize(query))
            } else {
                emptyList()
            }

            if (isActive) {
                updateState {
                    it.copy(isSearching = false, suggestions = suggestions)
                }
            }
        }
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            state.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        state.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: Map<Source, SearchItemResult> = mapOf(),
        val results: List<SmartSearchEngine.MergedResult> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val isSearching: Boolean = false,
        val completedSources: Int = 0,
        val totalSources: Int = 0,
        val dialog: Dialog? = null,
    ) {
        val progress: Int = completedSources
        val total: Int = totalSources

        /** Per-source items filtered by the "has results" toggle (used by migration search). */
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }

        /** Merged, ranked results for global search. */
        val filteredResults: List<SmartSearchEngine.MergedResult>
            get() = if (onlyShowHasResults) results.filter { it.score > 0f } else results
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
