package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Immutable
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String = "",
    preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : SearchScreenModel<GlobalSearchState>(GlobalSearchState(searchQuery = initialQuery)) {

    val incognitoMode = preferences.incognitoMode()
    val lastUsedSourceId = sourcePreferences.lastUsedSource()

    val searchPagerFlow = state.map { Pair(it.searchFilter, it.items) }
        .distinctUntilChanged()
        .map { (filter, items) ->
            items
                .filter { (source, result) -> isSourceVisible(filter, source, result) }
        }.stateIn(ioCoroutineScope, SharingStarted.Lazily, state.value.items)

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || initialExtensionFilter.isNotBlank()) {
            search(initialQuery)
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledSources().get()
        val pinnedSources = sourcePreferences.pinnedSources().get()

        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages }
            .filterNot { "${it.id}" in disabledSources }
            .sortedWith(compareBy({ "${it.id}" !in pinnedSources }, { "${it.name.lowercase()} (${it.lang})" }))
    }

    private fun isSourceVisible(filter: GlobalSearchFilter, source: CatalogueSource, result: SearchItemResult): Boolean {
        return when (filter) {
            GlobalSearchFilter.AvailableOnly -> result is SearchItemResult.Success && !result.isEmpty
            GlobalSearchFilter.PinnedOnly -> "${source.id}" in sourcePreferences.pinnedSources().get()
            GlobalSearchFilter.All -> true
        }
    }

    override fun updateSearchQuery(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    override fun updateItems(items: Map<CatalogueSource, SearchItemResult>) {
        mutableState.update {
            it.copy(items = items)
        }
    }

    fun setFilter(filter: GlobalSearchFilter) {
        mutableState.update { it.copy(searchFilter = filter) }
    }

    override fun getItems(): Map<CatalogueSource, SearchItemResult> {
        return mutableState.value.items
    }
}

enum class GlobalSearchFilter {
    All, PinnedOnly, AvailableOnly
}

@Immutable
data class GlobalSearchState(
    val searchQuery: String? = null,
    val searchFilter: GlobalSearchFilter = GlobalSearchFilter.All,
    val items: Map<CatalogueSource, SearchItemResult> = emptyMap(),
) {

    val progress: Int = items.count { it.value !is SearchItemResult.Loading }

    val total: Int = items.size
}
