package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.theme.header
import eu.kanade.presentation.util.bottomNavPaddingValues
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard

@Composable
fun MigrateSourceScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: MigrationSourcesPresenter,
    onClickItem: (Source) -> Unit,
) {
    val context = LocalContext.current
    when {
        presenter.isLoading -> LoadingScreen()
        presenter.isEmpty -> EmptyScreen(textResource = R.string.information_empty_library)
        else ->
            MigrateSourceList(
                nestedScrollInterop = nestedScrollInterop,
                list = presenter.items,
                onClickItem = onClickItem,
                onLongClickItem = { source ->
                    val sourceId = source.id.toString()
                    context.copyToClipboard(sourceId, sourceId)
                },
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
    ScrollbarLazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = bottomNavPaddingValues + WindowInsets.navigationBars.asPaddingValues() + topPaddingValues,
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
        icon = { SourceIcon(source = source) },
        action = {
            BadgeGroup {
                Badge(text = "$count")
            }
        },
        content = { source, showLanguageInContent ->
            Column(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .weight(1f),
            ) {
                Text(
                    text = source.name.ifBlank { source.id.toString() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showLanguageInContent) {
                        Text(
                            text = LocaleHelper.getDisplayName(source.lang),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (source.isStub) {
                        Text(
                            text = stringResource(R.string.not_installed),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}
