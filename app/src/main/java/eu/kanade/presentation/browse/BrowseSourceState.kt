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
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter.Filter
import eu.kanade.tachiyomi.ui.browse.source.browse.toItems

@Stable
interface BrowseSourceState {
    val source: CatalogueSource?
    var searchQuery: String?
    val currentFilter: Filter
    val isUserQuery: Boolean
    val filters: FilterList
    val filterItems: List<IFlexible<*>>
    var dialog: BrowseSourcePresenter.Dialog?
}

fun BrowseSourceState(initialQuery: String?): BrowseSourceState {
    return when (val filter = Filter.valueOf(initialQuery ?: "")) {
        Filter.Latest, Filter.Popular -> BrowseSourceStateImpl(initialCurrentFilter = filter)
        is Filter.UserInput -> BrowseSourceStateImpl(initialQuery = initialQuery, initialCurrentFilter = filter)
    }
}

class BrowseSourceStateImpl(initialQuery: String? = null, initialCurrentFilter: Filter) : BrowseSourceState {
    override var source: CatalogueSource? by mutableStateOf(null)
    override var searchQuery: String? by mutableStateOf(initialQuery)
    override var currentFilter: Filter by mutableStateOf(initialCurrentFilter)
    override val isUserQuery: Boolean by derivedStateOf { currentFilter is Filter.UserInput && currentFilter.query.isNotEmpty() }
    override var filters: FilterList by mutableStateOf(FilterList())
    override val filterItems: List<IFlexible<*>> by derivedStateOf { filters.toItems() }
    override var dialog: BrowseSourcePresenter.Dialog? by mutableStateOf(null)
}
