package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrationOriginSummary
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MigrateSearchScreen(
    state: SearchViewModel.State,
    fromSourceId: Long?,
    migrationOrigin: MigrationOriginSummary?,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                hideSourceFilter = true,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            migrationOrigin?.let { origin ->
                MigrationOriginBanner(origin)
            }
            Box(modifier = Modifier.weight(1f)) {
                GlobalSearchContent(
                    fromSourceId = fromSourceId,
                    items = state.filteredItems,
                    contentPadding = PaddingValues(),
                    getManga = getManga,
                    onClickSource = onClickSource,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                )
            }
        }
    }
}

@Composable
private fun MigrationOriginBanner(origin: MigrationOriginSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(MR.strings.migration_origin_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = origin.sourceName,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val latestChapter = origin.latestChapter?.let(::formatChapterNumber)
                ?: stringResource(MR.strings.migrationListScreen_unknownLatestChapter)
            Text(
                text = stringResource(MR.strings.migrationListScreen_latestChapterLabel, latestChapter),
                style = MaterialTheme.typography.bodySmall,
            )
            val latestDate = origin.latestUpload?.toLocalDate()?.toString()
                ?: stringResource(MR.strings.migrationListScreen_unknownLatestChapter)
            Text(
                text = stringResource(MR.strings.migration_origin_latest_upload, latestDate),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
}
