package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.browse.source.browse.toItems

@Stable
interface BrowseSourceState {
    val source: CatalogueSource?
    var searchQuery: String?
    val currentQuery: String
    val filters: FilterList
    val filterItems: List<IFlexible<*>>
    val appliedFilters: FilterList
    var dialog: BrowseSourcePresenter.Dialog?
}

fun BrowseSourceState(initialQuery: String?): BrowseSourceState {
    return BrowseSourceStateImpl(initialQuery)
}

class BrowseSourceStateImpl(initialQuery: String?) : BrowseSourceState {
    override var source: CatalogueSource? by mutableStateOf(null)
    override var searchQuery: String? by mutableStateOf(initialQuery)
    override var currentQuery: String by mutableStateOf(initialQuery ?: "")
    override var filters: FilterList by mutableStateOf(FilterList())
    override val filterItems: List<IFlexible<*>> by derivedStateOf { filters.toItems() }
    override var appliedFilters by mutableStateOf(FilterList())
    override var dialog: BrowseSourcePresenter.Dialog? by mutableStateOf(null)
}
