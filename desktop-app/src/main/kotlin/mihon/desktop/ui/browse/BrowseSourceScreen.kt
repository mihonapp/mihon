package mihon.desktop.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga

enum class SourceListingMode {
    Popular,
    Latest,
    Search,
}

data class BrowseSourceUiState(
    val source: SourceDescriptor,
    val mode: SourceListingMode = SourceListingMode.Popular,
    val query: String = "",
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val mangas: List<SManga> = emptyList(),
    val inLibraryUrls: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val filterList: FilterList = FilterList(),
    val isFilterDialogOpen: Boolean = false,
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            for (f in filterList) {
                when (f) {
                    is Filter.CheckBox -> if (f.state) count++
                    is Filter.Select<*> -> if (f.state > 0) count++
                    is Filter.Text -> if (f.state.isNotBlank()) count++
                    is Filter.TriState -> if (f.state != Filter.TriState.STATE_IGNORE) count++
                    is Filter.Group<*> -> {
                        for (child in f.state) {
                            if (child is Filter.CheckBox && child.state) count++
                            if (child is Filter.TriState && child.state != Filter.TriState.STATE_IGNORE) count++
                        }
                    }
                    is Filter.Sort -> {
                        if (f.state != null && (f.state!!.index != 0 || f.state!!.ascending)) count++
                    }
                    else -> {}
                }
            }
            return count
        }
}

@Composable
fun BrowseSourceScreen(
    state: BrowseSourceUiState,
    onBack: () -> Unit,
    onModeChange: (SourceListingMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPageChange: (Int) -> Unit,
    onMangaSelected: (SManga) -> Unit,
    onRetry: () -> Unit = {},
    onOpenFilters: () -> Unit = {},
    onCloseFilters: () -> Unit = {},
    onResetFilters: () -> Unit = {},
    onApplyFilters: (FilterList) -> Unit = {},
) {
    val strings = LocalStrings.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("browse-source-screen")) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.testTag("source-back-btn")) {
                    Text(strings.mangaDetailBack)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = state.source.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.browseSourceLanguage(state.source.lang.uppercase()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Mode chips
            Row {
                FilterChip(
                    selected = state.mode == SourceListingMode.Popular,
                    onClick = { onModeChange(SourceListingMode.Popular) },
                    label = { Text(strings.browseSourcePopular) },
                    modifier = Modifier.testTag("mode-popular-chip"),
                )
                if (state.source.supportsLatest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = state.mode == SourceListingMode.Latest,
                        onClick = { onModeChange(SourceListingMode.Latest) },
                        label = { Text(strings.browseSourceLatest) },
                        modifier = Modifier.testTag("mode-latest-chip"),
                    )
                }
            }
        }

        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(strings.browseSearchTitlesPlaceholder) },
                modifier = Modifier.weight(1f).testTag("source-search-input"),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSearch,
                modifier = Modifier.testTag("source-search-btn"),
            ) {
                Text(strings.browseSearchButton)
            }
            if (state.filterList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.testTag("source-filter-btn"),
                ) {
                    val count = state.activeFilterCount
                    Text(strings.browseFiltersButton(count))
                }
            }
        }

        // Error message
        state.errorMessage?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text(strings.libraryRetry) }
            }
        }

        // Main Grid or Loading
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("source-loading-indicator"))
            }
        } else if (state.mangas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    strings.browseNoMangaFound,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f).testTag("manga-grid"),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.mangas, key = { it.url }) { manga ->
                    val inLibrary = state.inLibraryUrls.contains(manga.url)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMangaSelected(manga) }
                            .testTag("manga-card-" + manga.url),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                mihon.desktop.ui.common.MangaCover(
                                    thumbnailUrl = manga.thumbnailUrl,
                                    contentDescription = manga.title,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (inLibrary) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    ) {
                                        Text(
                                            text = strings.browseInLibraryBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Pagination Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { if (state.page > 1) onPageChange(state.page - 1) },
                enabled = state.page > 1 && !state.isLoading,
                modifier = Modifier.testTag("prev-page-btn"),
            ) {
                Text(strings.browsePrevPage)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = strings.browsePageNumber(state.page),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(
                onClick = { if (state.hasNextPage) onPageChange(state.page + 1) },
                enabled = state.hasNextPage && !state.isLoading,
                modifier = Modifier.testTag("next-page-btn"),
            ) {
                Text(strings.browseNextPage)
            }
        }

        if (state.isFilterDialogOpen) {
            SourceFilterDialog(
                filterList = state.filterList,
                onDismissRequest = onCloseFilters,
                onReset = onResetFilters,
                onApply = { applied ->
                    onApplyFilters(applied)
                },
            )
        }
    }
}
