package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchFilters
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchStatus
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.DoneAll
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Keep
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    hideSourceFilter: Boolean,
    sourceFilter: SourceFilter,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    activeFilters: GlobalSearchFilters = GlobalSearchFilters(),
    availableTags: List<String> = emptyList(),
    onToggleStatusFilter: ((GlobalSearchStatus) -> Unit)? = null,
    onToggleTagFilter: ((String) -> Unit)? = null,
    onClearFilters: (() -> Unit)? = null,
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                onClickCloseSearch = navigateUp,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
            if (progress in 1..<total) {
                LinearProgressIndicator(
                    progress = { progress / total.toFloat() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO: make this UX better; it only applies when triggering a new search
            if (!hideSourceFilter) {
                FilterChip(
                    selected = sourceFilter == SourceFilter.PinnedOnly,
                    onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Keep,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.pinned_sources))
                    },
                )
                FilterChip(
                    selected = sourceFilter == SourceFilter.All,
                    onClick = { onChangeSearchFilter(SourceFilter.All) },
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.all))
                    },
                )

                VerticalDivider(modifier = Modifier.height(FilterChipDefaults.Height))
            }

            FilterChip(
                selected = onlyShowHasResults,
                onClick = { onToggleResults() },
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier
                            .size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )

            if (onToggleStatusFilter != null) {
                VerticalDivider(modifier = Modifier.height(FilterChipDefaults.Height))

                Box {
                    val selectedStatuses = activeFilters.statuses
                    FilterChip(
                        selected = selectedStatuses.isNotEmpty(),
                        onClick = { showStatusMenu = true },
                        label = {
                            val labelText = when (selectedStatuses.size) {
                                0 -> stringResource(MR.strings.status)
                                1 -> stringResource(selectedStatuses.first().labelRes)
                                else -> "${stringResource(MR.strings.status)} (${selectedStatuses.size})"
                            }
                            Text(text = labelText)
                        },
                    )

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false },
                    ) {
                        GlobalSearchStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(text = stringResource(status.labelRes)) },
                                leadingIcon = {
                                    Checkbox(
                                        checked = status in selectedStatuses,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = { onToggleStatusFilter(status) },
                            )
                        }
                    }
                }
            }

            if (onToggleTagFilter != null &&
                (
                    availableTags.isNotEmpty() ||
                        activeFilters.includedTags.isNotEmpty() ||
                        activeFilters.excludedTags.isNotEmpty()
                    )
            ) {
                val tagCount = activeFilters.includedTags.size + activeFilters.excludedTags.size
                FilterChip(
                    selected = tagCount > 0,
                    onClick = { showTagDialog = true },
                    label = {
                        val labelText = if (tagCount == 0) {
                            stringResource(MR.strings.action_filter)
                        } else {
                            "${stringResource(MR.strings.action_filter)} ($tagCount)"
                        }
                        Text(text = labelText)
                    },
                )

                activeFilters.includedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onToggleTagFilter(tag) },
                        label = { Text(text = "+$tag") },
                        trailingIcon = {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }

                activeFilters.excludedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onToggleTagFilter(tag) },
                        label = { Text(text = "-$tag") },
                        trailingIcon = {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }

            if (onClearFilters != null && !activeFilters.isEmpty) {
                FilterChip(
                    selected = false,
                    onClick = onClearFilters,
                    label = { Text(text = stringResource(MR.strings.action_reset)) },
                )
            }
        }

        HorizontalDivider()
    }

    if (showTagDialog && onToggleTagFilter != null) {
        GlobalSearchTagDialog(
            availableTags = availableTags,
            includedTags = activeFilters.includedTags,
            excludedTags = activeFilters.excludedTags,
            onToggleTag = onToggleTagFilter,
            onDismissRequest = { showTagDialog = false },
        )
    }
}

@Composable
private fun GlobalSearchTagDialog(
    availableTags: List<String>,
    includedTags: Set<String>,
    excludedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var filterText by remember { mutableStateOf("") }
    val filteredTags = remember(availableTags, filterText) {
        if (filterText.isBlank()) {
            availableTags
        } else {
            availableTags.filter { it.contains(filterText.trim(), ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.action_filter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(MR.strings.action_search)) },
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filteredTags, key = { it }) { tag ->
                        val triState = when {
                            includedTags.any { it.equals(tag, ignoreCase = true) } -> TriState.ENABLED_IS
                            excludedTags.any { it.equals(tag, ignoreCase = true) } -> TriState.ENABLED_NOT
                            else -> TriState.DISABLED
                        }
                        TriStateItem(
                            label = tag,
                            state = triState,
                            onClick = { onToggleTag(tag) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
