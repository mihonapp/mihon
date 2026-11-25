package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal

abstract class SearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    private val preferences: SourcePreferences,
    private val context: Context? = null,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(initialState)

    // Subclasses can't touch the backing field (Kotlin forbids a visibility modifier on one),
    // so state writes from them go through here.
    protected fun updateState(function: (State) -> State) {
        state.update(function)
    }

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    protected val pinnedSources = sourcePreferences.pinnedSources.get()

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null
    private var lastActiveFilters: GlobalSearchFilters? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { onlyShowHasResults ->
                state.update { it.copy(onlyShowHasResults = onlyShowHasResults) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadAvailableFilterOptions()
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
            .filter { (it.lang in enabledLanguages || it.isLocal()) && "${it.id}" !in disabledSources }
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

        return extensionManager.getLoadedExtensions()
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filter { it in enabledSources }
    }

    /**
     * Collects tag/genre names exposed by enabled extensions' filter lists so the UI chip sheet
     * automatically adapts to the user's installed sources and active extension languages.
     */
    private suspend fun loadAvailableFilterOptions() {
        val tagCounts = mutableMapOf<String, Int>()
        val displayNames = mutableMapOf<String, String>()

        for (source in getSelectedSources()) {
            val filters = runCatching { source.getFilterList() }.getOrNull() ?: continue
            val seenInSource = mutableSetOf<String>()

            fun collectFrom(filter: Filter<*>, insideExcludedGroup: Boolean) {
                val lowerName = filter.name.lowercase()
                val excludedGroup = insideExcludedGroup ||
                    lowerName.contains("status") ||
                    lowerName.contains("scanlation") ||
                    lowerName.contains("translation") ||
                    lowerName.contains("upload")
                if (excludedGroup) return

                when (filter) {
                    is Filter.Group<*> -> {
                        filter.state.filterIsInstance<Filter<*>>().forEach {
                            collectFrom(it, insideExcludedGroup = false)
                        }
                    }
                    is Filter.TriState, is Filter.CheckBox -> {
                        val trimmed = filter.name.trim()
                        val key = trimmed.lowercase()
                        if (trimmed.isNotEmpty() &&
                            GlobalSearchStatus.fromString(trimmed, context) == null &&
                            seenInSource.add(key)
                        ) {
                            tagCounts[key] = (tagCounts[key] ?: 0) + 1
                            displayNames.putIfAbsent(key, trimmed)
                        }
                    }
                    else -> {}
                }
            }

            filters.forEach { collectFrom(it, insideExcludedGroup = false) }
        }

        val sortedTags = tagCounts.entries
            .sortedWith(
                compareBy(
                    { -it.value },
                    { it.key },
                ),
            )
            .mapNotNull { displayNames[it.key] }

        state.update { it.copy(availableTags = sortedTags) }
    }

    fun updateSearchQuery(query: String?) {
        val parsed = parseGlobalSearchQuery(query.orEmpty(), context)
        state.update {
            it.copy(
                searchQuery = query,
                invalidFilterTokens = parsed.invalidTokens,
            )
        }
    }

    fun setSourceFilter(filter: SourceFilter) {
        state.update { it.copy(sourceFilter = filter) }
        viewModelScope.launch(Dispatchers.IO) {
            loadAvailableFilterOptions()
        }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    fun toggleStatusFilter(status: GlobalSearchStatus) {
        state.update { current ->
            val updated = if (status in current.activeFilters.statuses) {
                current.activeFilters.statuses - status
            } else {
                current.activeFilters.statuses + status
            }
            current.copy(activeFilters = current.activeFilters.copy(statuses = updated))
        }
        search(forceRefresh = true)
    }

    fun toggleTagFilter(tag: String) {
        state.update { current ->
            val filters = current.activeFilters
            val existingInclude = filters.includedTags.firstOrNull { it.equals(tag, ignoreCase = true) }
            val existingExclude = filters.excludedTags.firstOrNull { it.equals(tag, ignoreCase = true) }

            val updatedFilters = when {
                existingInclude != null -> filters.copy(
                    includedTags = filters.includedTags - existingInclude,
                    excludedTags = filters.excludedTags + tag,
                )
                existingExclude != null -> filters.copy(
                    excludedTags = filters.excludedTags - existingExclude,
                )
                else -> filters.copy(
                    includedTags = filters.includedTags + tag,
                )
            }
            current.copy(activeFilters = updatedFilters)
        }
        search(forceRefresh = true)
    }

    fun setSortKeyword(sortKeyword: String?) {
        state.update { current ->
            current.copy(activeFilters = current.activeFilters.copy(sortKeyword = sortKeyword))
        }
        search(forceRefresh = true)
    }

    fun clearGlobalFilters() {
        val cleanedQuery = state.value.searchQuery
            ?.let { parseGlobalSearchQuery(it, context).cleanQuery }
        state.update {
            it.copy(
                searchQuery = cleanedQuery,
                activeFilters = GlobalSearchFilters(),
                invalidFilterTokens = emptyList(),
            )
        }
        search(forceRefresh = true)
    }

    fun search(forceRefresh: Boolean = false) {
        val rawQuery = state.value.searchQuery.orEmpty()
        val sourceFilter = state.value.sourceFilter
        val uiFilters = state.value.activeFilters

        val parsed = parseGlobalSearchQuery(rawQuery, context)
        val effectiveFilters = GlobalSearchFilters(
            statuses = uiFilters.statuses + parsed.filters.statuses,
            includedTags = uiFilters.includedTags + parsed.filters.includedTags,
            excludedTags = uiFilters.excludedTags + parsed.filters.excludedTags,
            sortKeyword = parsed.filters.sortKeyword ?: uiFilters.sortKeyword,
        )

        if (parsed.cleanQuery.isBlank() && effectiveFilters.isEmpty) return

        val sameQuery = !forceRefresh &&
            this.lastQuery == parsed.cleanQuery &&
            this.lastActiveFilters == effectiveFilters
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = parsed.cleanQuery
        this.lastSourceFilter = sourceFilter
        this.lastActiveFilters = effectiveFilters

        state.update { it.copy(invalidFilterTokens = parsed.invalidTokens) }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val sources = getSelectedSources()
            if (sameQuery) {
                val existingResults = state.value.items
                updateItems(
                    sources.associateWith { existingResults[it] ?: SearchItemResult.Loading },
                )
            } else {
                updateItems(
                    sources.associateWith { SearchItemResult.Loading },
                )
            }

            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }
                    try {
                        val (page, report) = withContext(coroutineDispatcher) {
                            val filters = source.getFilterList()
                            val applyReport = filters.applyGlobalFilters(effectiveFilters, context)
                            val searchPage = source.getSearchManga(1, parsed.cleanQuery, filters)
                            searchPage to applyReport
                        }
                        val titles = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }
                            .applyClientSideFallback(effectiveFilters, report)

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        state.update {
            it.copy(
                items = items.toSortedMap(sortComparator(items)),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        state.update { currentState ->
            val newItems = currentState.items + (source to result)
            currentState.copy(
                items = newItems.toSortedMap(sortComparator(newItems)),
            )
        }
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = getManga.await(currentId) ?: return@launch
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
        val activeFilters: GlobalSearchFilters = GlobalSearchFilters(),
        val availableTags: List<String> = emptyList(),
        val invalidFilterTokens: List<String> = emptyList(),
        val items: Map<Source, SearchItemResult> = mapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
        val hasActiveFilters: Boolean
            get() = !activeFilters.isEmpty
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
