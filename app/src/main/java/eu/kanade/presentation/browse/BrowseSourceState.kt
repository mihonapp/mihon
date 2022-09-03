package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.source.interactor.GetRemoteManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.toItems

@Stable
interface BrowseSourceState {
    val source: CatalogueSource?
    var searchQuery: String?
    val currentQuery: String
    val isUserQuery: Boolean
    val filters: FilterList
    val filterItems: List<IFlexible<*>>
    val currentFilters: FilterList
    var dialog: BrowseSourcePresenter.Dialog?
}

fun BrowseSourceState(initialQuery: String?): BrowseSourceState {
    if (initialQuery == GetRemoteManga.QUERY_POPULAR || initialQuery == GetRemoteManga.QUERY_LATEST) {
        return BrowseSourceStateImpl(initialCurrentQuery = initialQuery)
    }
    return BrowseSourceStateImpl(initialQuery = initialQuery)
}

class BrowseSourceStateImpl(initialQuery: String? = null, initialCurrentQuery: String? = initialQuery) : BrowseSourceState {
    override var source: CatalogueSource? by mutableStateOf(null)
    override var searchQuery: String? by mutableStateOf(initialQuery)
    override var currentQuery: String by mutableStateOf(initialCurrentQuery ?: "")
    override val isUserQuery: Boolean by derivedStateOf {
        currentQuery.isNotEmpty() &&
            currentQuery != GetRemoteManga.QUERY_POPULAR &&
            currentQuery != GetRemoteManga.QUERY_LATEST
    }
    override var filters: FilterList by mutableStateOf(FilterList())
    override val filterItems: List<IFlexible<*>> by derivedStateOf { filters.toItems() }
    override var currentFilters by mutableStateOf(FilterList())
    override var dialog: BrowseSourcePresenter.Dialog? by mutableStateOf(null)
}
