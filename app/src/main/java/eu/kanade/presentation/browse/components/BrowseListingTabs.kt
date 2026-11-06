package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.NeonTabRow
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Verified
import mihon.icons.materialsymbols.roundedfilled.Favorite
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The listings of a source as a segmented pill: Popular, Latest when the source has it, and Filter when it has
 * filters, which is selected while searching.
 */
@Composable
fun BrowseListingTabs(
    listing: Listing,
    supportsLatest: Boolean,
    hasFilters: Boolean,
    onPopular: () -> Unit,
    onLatest: () -> Unit,
    onFilter: () -> Unit,
) {
    val tabs = buildList {
        add(ListingTab(MR.strings.popular, MaterialSymbols.RoundedFilled.Favorite, onPopular) { it == Listing.Popular })
        if (supportsLatest) {
            add(ListingTab(MR.strings.latest, MaterialSymbols.Rounded.Verified, onLatest) { it == Listing.Latest })
        }
        if (hasFilters) {
            add(
                ListingTab(MR.strings.action_filter, MaterialSymbols.Rounded.FilterList, onFilter) {
                    it is Listing.Search
                },
            )
        }
    }
    NeonTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.isSelected(listing) },
        tabCount = tabs.size,
        onTabClick = { tabs[it].onClick() },
    ) { index, _ ->
        val tab = tabs[index]
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = stringResource(tab.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private class ListingTab(
    val title: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isSelected: (Listing) -> Boolean,
)
