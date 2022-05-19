package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.ItemBadges
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrateSourceState
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesPresenter

@Composable
fun MigrateSourceScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: MigrationSourcesPresenter,
    onClickItem: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
) {
    val state by presenter.state.collectAsState()
    when (state) {
        is MigrateSourceState.Loading -> LoadingScreen()
        is MigrateSourceState.Error -> Text(text = (state as MigrateSourceState.Error).error.message!!)
        is MigrateSourceState.Success ->
            MigrateSourceList(
                nestedScrollInterop = nestedScrollInterop,
                list = (state as MigrateSourceState.Success).sources,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
            )
    }
}

@Composable
fun MigrateSourceList(
    nestedScrollInterop: NestedScrollConnection,
    list: List<Pair<Source, Long>>,
    onClickItem: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
) {
    if (list.isEmpty()) {
        EmptyScreen(textResource = R.string.information_empty_library)
        return
    }

    LazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues() + topPaddingValues,
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.migration_selection_prompt),
                modifier = Modifier
                    .animateItemPlacement()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                style = MaterialTheme.typography.header,
            )
        }

        items(
            items = list,
            key = { (source, _) ->
                source.id
            },
        ) { (source, count) ->
            MigrateSourceItem(
                modifier = Modifier.animateItemPlacement(),
                source = source,
                count = count,
                onClickItem = { onClickItem(source) },
                onLongClickItem = { onLongClickItem(source) },
            )
        }
    }
}

@Composable
fun MigrateSourceItem(
    modifier: Modifier = Modifier,
    source: Source,
    count: Long,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        showLanguageInContent = source.lang != "",
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        action = { ItemBadges(primaryText = "$count") },
    )
}
