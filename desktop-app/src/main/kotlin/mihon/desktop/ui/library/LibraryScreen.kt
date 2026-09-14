package mihon.desktop.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.MangaCover

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    detailState: MangaDetailUiState = MangaDetailUiState(),
    onQueryChange: (String) -> Unit,
    onMangaSelected: (Long) -> Unit,
    onBackFromDetail: () -> Unit = {},
    onReadChapter: (Long) -> Unit = {},
    onDetailRetry: () -> Unit = {},
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onRetry: () -> Unit = {},
    onCategorySelected: (Long) -> Unit = {},
    onManageCategories: () -> Unit = {},
    onEditMangaCategories: () -> Unit = {},
    onOpenTracking: () -> Unit = {},
    // Phase 11: Display mode, grid zoom, filter & sort, selection callbacks
    onDisplayModeChange: (LibraryDisplayMode) -> Unit = {},
    onGridSizeChange: (Float) -> Unit = {},
    onOpenFilterDialog: () -> Unit = {},
    onCloseFilterDialog: () -> Unit = {},
    onFilterChange: (LibraryFilterState) -> Unit = {},
    onSortChange: (LibrarySortState) -> Unit = {},
    onToggleSelectionMode: (Boolean) -> Unit = {},
    onToggleMangaSelection: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onBatchChangeCategories: () -> Unit = {},
    onBatchSetCategories: (List<Long>) -> Unit = {},
    onBatchCloseCategoryDialog: () -> Unit = {},
    onBatchMarkRead: (Boolean) -> Unit = {},
    onBatchDownload: (Int) -> Unit = {},
    onBatchRemoveFromLibrary: () -> Unit = {},
    isUpdatingLibrary: Boolean = false,
    onUpdateLibrary: (() -> Unit)? = null,
    // Phase 16: Edit info, chapter filter/sort & actions
    onEditInfo: () -> Unit = {},
    onDismissEditInfo: () -> Unit = {},
    onSaveMangaInfo: (
        title: String,
        author: String?,
        artist: String?,
        description: String?,
        genres: List<String>,
        status: Long,
        notes: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onResetMangaInfo: () -> Unit = {},
    onChapterFilterChange: (ChapterFilterState) -> Unit = {},
    onChapterSortChange: (ChapterSortState) -> Unit = {},
    onToggleBookmark: (Long) -> Unit = {},
    onToggleRead: (Long) -> Unit = {},
    onMarkPreviousRead: (Long) -> Unit = {},
    onDownloadChapter: (Long) -> Unit = {},
    onDeleteDownload: (Long) -> Unit = {},
    onDownloadBatch: (Int?) -> Unit = {},
    onBatchBookmarkChapters: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleBookmark) },
    onBatchMarkChaptersRead: (Set<Long>, Boolean) -> Unit = { ids, _ -> ids.forEach(onToggleRead) },
    onBatchDownloadChapters: (Set<Long>) -> Unit = { ids -> ids.forEach(onDownloadChapter) },
    onBatchDeleteDownloads: (Set<Long>) -> Unit = { ids -> ids.forEach(onDeleteDownload) },
    onOpenChapterSettings: () -> Unit = {},
    onDismissChapterSettings: () -> Unit = {},
    onChapterDisplayModeChange: (ChapterDisplayMode) -> Unit = {},
    onExcludedScanlatorsChange: (Set<String>) -> Unit = {},
    onShowMissingChaptersChange: (Boolean) -> Unit = {},
    onSetChapterSettingsAsDefault: (Boolean) -> Unit = {},
    onResetChapterSettingsToDefault: () -> Unit = {},
    mangaDetailActions: MangaDetailActions? = null,
    onToggleMangaLibrary: (() -> Unit)? = null,
    onRefreshMangaSource: (() -> Unit)? = null,
    isMangaLibraryActionRunning: Boolean = false,
    isMangaSourceRefreshing: Boolean = false,
    onDuplicateOpenManga: (Long) -> Unit = {},
    onDuplicateMigrate: (Long) -> Unit = {},
    onDuplicateAddAnyway: () -> Unit = {},
    onDuplicateDismiss: () -> Unit = {},
    sourceNameFor: (Long) -> String = { "Source #$it" },
) {
    val resolvedMangaDetailActions = mangaDetailActions ?: MangaDetailActions(
        onReadChapter = onReadChapter,
        onEditCategories = onEditMangaCategories,
        onOpenTracking = onOpenTracking,
        onEditInfo = onEditInfo,
        onDismissEditInfo = onDismissEditInfo,
        onSaveMangaInfo = onSaveMangaInfo,
        onResetMangaInfo = onResetMangaInfo,
        onChapterFilterChange = onChapterFilterChange,
        onChapterSortChange = onChapterSortChange,
        onToggleBookmark = onToggleBookmark,
        onToggleRead = onToggleRead,
        onMarkPreviousRead = onMarkPreviousRead,
        onDownloadChapter = onDownloadChapter,
        onDeleteDownload = onDeleteDownload,
        onDownloadBatch = onDownloadBatch,
        onBatchBookmarkChapters = onBatchBookmarkChapters,
        onBatchMarkChaptersRead = onBatchMarkChaptersRead,
        onBatchDownloadChapters = onBatchDownloadChapters,
        onBatchDeleteDownloads = onBatchDeleteDownloads,
        onOpenChapterSettings = onOpenChapterSettings,
        onDismissChapterSettings = onDismissChapterSettings,
        onChapterDisplayModeChange = onChapterDisplayModeChange,
        onExcludedScanlatorsChange = onExcludedScanlatorsChange,
        onShowMissingChaptersChange = onShowMissingChaptersChange,
        onSetChapterSettingsAsDefault = onSetChapterSettingsAsDefault,
        onResetChapterSettingsToDefault = onResetChapterSettingsToDefault,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("library-screen")) {
        val selected = state.selectedMangaId != null
        val wide = maxWidth >= 1100.dp
        if (wide && selected) {
            Row(modifier = Modifier.fillMaxSize()) {
                LibraryPane(
                    state = state,
                    onQueryChange = onQueryChange,
                    onMangaSelected = onMangaSelected,
                    onImportBackup = onImportBackup,
                    onImportLocal = onImportLocal,
                    onUpdateLibrary = onUpdateLibrary,
                    isUpdatingLibrary = isUpdatingLibrary,
                    onRetry = onRetry,
                    onCategorySelected = onCategorySelected,
                    onManageCategories = onManageCategories,
                    onDisplayModeChange = onDisplayModeChange,
                    onGridSizeChange = onGridSizeChange,
                    onOpenFilterDialog = onOpenFilterDialog,
                    onToggleSelectionMode = onToggleSelectionMode,
                    onToggleMangaSelection = onToggleMangaSelection,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onBatchChangeCategories = onBatchChangeCategories,
                    onBatchMarkRead = onBatchMarkRead,
                    onBatchDownload = onBatchDownload,
                    onBatchRemoveFromLibrary = onBatchRemoveFromLibrary,
                    modifier = Modifier.weight(0.55f).fillMaxHeight().testTag("library-grid-pane"),
                )
                VerticalDivider()
                MangaDetailScreen(
                    state = detailState,
                    onBack = onBackFromDetail,
                    actions = resolvedMangaDetailActions,
                    onRetry = onDetailRetry,
                    onToggleLibrary = onToggleMangaLibrary,
                    onRefreshSource = onRefreshMangaSource,
                    isLibraryActionRunning = isMangaLibraryActionRunning,
                    isRefreshingSource = isMangaSourceRefreshing,
                    showBack = false,
                    modifier = Modifier.weight(0.45f).fillMaxHeight(),
                )
            }
        } else if (selected) {
            MangaDetailScreen(
                state = detailState,
                onBack = onBackFromDetail,
                actions = resolvedMangaDetailActions,
                onRetry = onDetailRetry,
                onToggleLibrary = onToggleMangaLibrary,
                onRefreshSource = onRefreshMangaSource,
                isLibraryActionRunning = isMangaLibraryActionRunning,
                isRefreshingSource = isMangaSourceRefreshing,
                showBack = true,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LibraryPane(
                state = state,
                onQueryChange = onQueryChange,
                onMangaSelected = onMangaSelected,
                onImportBackup = onImportBackup,
                onImportLocal = onImportLocal,
                onUpdateLibrary = onUpdateLibrary,
                isUpdatingLibrary = isUpdatingLibrary,
                onRetry = onRetry,
                onCategorySelected = onCategorySelected,
                onManageCategories = onManageCategories,
                onDisplayModeChange = onDisplayModeChange,
                onGridSizeChange = onGridSizeChange,
                onOpenFilterDialog = onOpenFilterDialog,
                onToggleSelectionMode = onToggleSelectionMode,
                onToggleMangaSelection = onToggleMangaSelection,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onBatchChangeCategories = onBatchChangeCategories,
                onBatchMarkRead = onBatchMarkRead,
                onBatchDownload = onBatchDownload,
                onBatchRemoveFromLibrary = onBatchRemoveFromLibrary,
                modifier = Modifier.fillMaxSize().testTag("library-grid-pane"),
            )
        }

        // Filter & Sort Dialog
        if (state.isFilterDialogOpen) {
            LibraryFilterDialog(
                filterState = state.filterState,
                sortState = state.sortState,
                onFilterChange = onFilterChange,
                onSortChange = onSortChange,
                onDismiss = onCloseFilterDialog,
            )
        }

        // Batch Category Dialog
        if (state.isBatchCategoryDialogOpen) {
            BatchCategorySelectionDialog(
                categories = state.categories.filter { it.id != SYSTEM_ALL_CATEGORY.id },
                onDismiss = onBatchCloseCategoryDialog,
                onConfirm = onBatchSetCategories,
            )
        }

        state.duplicateDialog?.let { duplicateDialog ->
            DuplicateMangaDialog(
                state = duplicateDialog,
                sourceNameFor = sourceNameFor,
                onDismissRequest = onDuplicateDismiss,
                onAddAnyway = onDuplicateAddAnyway,
                onOpenManga = onDuplicateOpenManga,
                onMigrate = onDuplicateMigrate,
            )
        }
    }
}

@Composable
private fun LibraryPane(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onMangaSelected: (Long) -> Unit,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onRetry: () -> Unit,
    onCategorySelected: (Long) -> Unit,
    onManageCategories: () -> Unit,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onGridSizeChange: (Float) -> Unit,
    onOpenFilterDialog: () -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onToggleMangaSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBatchChangeCategories: () -> Unit,
    onBatchMarkRead: (Boolean) -> Unit,
    onBatchDownload: (Int) -> Unit,
    onBatchRemoveFromLibrary: () -> Unit,
    isUpdatingLibrary: Boolean = false,
    onUpdateLibrary: (() -> Unit)? = null,
    modifier: Modifier,
) {
    val strings = LocalStrings.current

    BoxWithConstraints(modifier = modifier) {
        val useNarrowControls = maxWidth < 900.dp
        val headerActions: @Composable () -> Unit = {
            if (onUpdateLibrary != null) {
                Button(
                    onClick = onUpdateLibrary,
                    enabled = !isUpdatingLibrary,
                    modifier = Modifier.testTag("library-update-button"),
                ) {
                    if (isUpdatingLibrary) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.libraryUpdating)
                    } else {
                        Text(strings.libraryUpdateNow)
                    }
                }
            }
            FilledTonalButton(
                onClick = onImportBackup,
                modifier = Modifier.testTag("library-import-backup"),
            ) {
                Text(strings.libraryImportBackup)
            }
            FilledTonalButton(
                onClick = onImportLocal,
                modifier = Modifier.testTag("library-import-local"),
            ) {
                Text(strings.libraryImportLocal)
            }
        }
        val displayModeControls: @Composable () -> Unit = {
            val modes = listOf(
                LibraryDisplayMode.ComfortableGrid to strings.libraryDisplayComfortable,
                LibraryDisplayMode.CompactGrid to strings.libraryDisplayCompact,
                LibraryDisplayMode.CoverOnly to strings.libraryDisplayCoverOnly,
                LibraryDisplayMode.List to strings.libraryDisplayList,
            )
            for ((mode, label) in modes) {
                FilterChip(
                    selected = state.displayMode == mode,
                    onClick = { onDisplayModeChange(mode) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("display-mode-${mode.name}"),
                )
            }
        }
        val libraryActionControls: @Composable () -> Unit = {
            if (state.displayMode != LibraryDisplayMode.List) {
                Text(
                    text = "${state.gridSize.toInt()}dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Slider(
                    value = state.gridSize,
                    onValueChange = onGridSizeChange,
                    valueRange = 120f..280f,
                    modifier = Modifier.width(100.dp).testTag("library-grid-slider"),
                )
            }

            BadgedBox(
                badge = {
                    if (state.filterState.hasActiveFilters) {
                        Badge(modifier = Modifier.testTag("filter-active-badge")) {
                            Text(state.filterState.activeCount.toString())
                        }
                    }
                },
            ) {
                OutlinedButton(
                    onClick = onOpenFilterDialog,
                    modifier = Modifier.testTag("library-filter-sort-button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.libraryFilterAndSort)
                }
            }

            FilterChip(
                selected = state.selectionState.isSelectionMode,
                onClick = { onToggleSelectionMode(!state.selectionState.isSelectionMode) },
                label = { Text(strings.libraryBatchSelect) },
                modifier = Modifier.testTag("library-toggle-selection"),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (useNarrowControls) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = strings.libraryTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        headerActions()
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.libraryTitle,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    headerActions()
                }
            }

            if (useNarrowControls) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    displayModeControls()
                    libraryActionControls()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        displayModeControls()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        libraryActionControls()
                    }
                }
            }

            // Category Chips Row
            Row(
                modifier = Modifier.fillMaxWidth().testTag("library-categories-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(state.categories, key = { it.id }) { cat ->
                        val label = if (cat.id == SYSTEM_ALL_CATEGORY.id) strings.libraryAllCategory else cat.name
                        val isSelected = cat.id == state.selectedCategoryId
                        val totalCount = if (isSelected) state.items.size else null
                        val unreadCount = if (isSelected) state.items.sumOf { it.unreadCount }.toInt() else 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(cat.id) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(label)
                                    if (isSelected && totalCount != null && totalCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (unreadCount >
                                                0
                                            ) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (unreadCount >
                                                0
                                            ) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        ) {
                                            Text(
                                                text = if (unreadCount > 0) "$unreadCount" else "$totalCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("library-category-chip-${cat.id}"),
                        )
                    }
                }
                OutlinedButton(
                    onClick = onManageCategories,
                    modifier = Modifier.testTag("library-manage-categories-button"),
                ) {
                    Text(strings.libraryManageCategories)
                }
            }

            // Search Box
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("library-search"),
                label = { Text(strings.librarySearchPlaceholder) },
                singleLine = true,
            )

            // Manga Content Grid / List
            LibraryContent(
                state = state,
                onMangaSelected = onMangaSelected,
                onToggleMangaSelection = onToggleMangaSelection,
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        // Floating Batch Action Bar at bottom
        if (state.selectionState.isSelectionMode || state.selectionState.isAnySelected) {
            LibraryBatchActionBar(
                selectedCount = state.selectionState.count,
                totalCount = state.items.size,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onChangeCategories = onBatchChangeCategories,
                onMarkRead = onBatchMarkRead,
                onDownloadChapters = onBatchDownload,
                onRemoveFromLibrary = onBatchRemoveFromLibrary,
                onExitSelection = { onToggleSelectionMode(false) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onMangaSelected: (Long) -> Unit,
    onToggleMangaSelection: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when {
        state.loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag("library-loading"))
        }
        state.errorMessage != null -> ErrorState(state.errorMessage, onRetry, modifier)
        state.items.isEmpty() -> EmptyState(state.query, modifier)
        else -> BoxWithConstraints(modifier = modifier) {
            when (state.displayMode) {
                LibraryDisplayMode.ComfortableGrid -> {
                    val spacing = 16.dp
                    val metrics = calculateLibraryGridMetrics(
                        mode = state.displayMode,
                        availableWidthDp = maxWidth.value,
                        requestedWidthDp = state.gridSize,
                        spacingDp = spacing.value,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(metrics.columns),
                        modifier = Modifier.fillMaxSize().testTag("library-view-comfortable"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            ComfortableMangaCard(
                                manga = manga,
                                isSelectionMode = state.selectionState.isSelectionMode,
                                isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                onClick = {
                                    if (state.selectionState.isSelectionMode) {
                                        onToggleMangaSelection(manga.id)
                                    } else {
                                        onMangaSelected(manga.id)
                                    }
                                },
                            )
                        }
                    }
                }
                LibraryDisplayMode.CompactGrid -> {
                    val spacing = 12.dp
                    val metrics = calculateLibraryGridMetrics(
                        mode = state.displayMode,
                        availableWidthDp = maxWidth.value,
                        requestedWidthDp = state.gridSize,
                        spacingDp = spacing.value,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(metrics.columns),
                        modifier = Modifier.fillMaxSize().testTag("library-view-compact"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            CompactMangaCard(
                                manga = manga,
                                isSelectionMode = state.selectionState.isSelectionMode,
                                isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                onClick = {
                                    if (state.selectionState.isSelectionMode) {
                                        onToggleMangaSelection(manga.id)
                                    } else {
                                        onMangaSelected(manga.id)
                                    }
                                },
                            )
                        }
                    }
                }
                LibraryDisplayMode.CoverOnly -> {
                    val spacing = 10.dp
                    val metrics = calculateLibraryGridMetrics(
                        mode = state.displayMode,
                        availableWidthDp = maxWidth.value,
                        requestedWidthDp = state.gridSize,
                        spacingDp = spacing.value,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(metrics.columns),
                        modifier = Modifier.fillMaxSize().testTag("library-view-coveronly"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            CoverOnlyMangaCard(
                                manga = manga,
                                isSelectionMode = state.selectionState.isSelectionMode,
                                isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                onClick = {
                                    if (state.selectionState.isSelectionMode) {
                                        onToggleMangaSelection(manga.id)
                                    } else {
                                        onMangaSelected(manga.id)
                                    }
                                },
                            )
                        }
                    }
                }
                LibraryDisplayMode.List -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("library-view-list"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.items, key = LibraryManga::id) { manga ->
                            ListMangaItem(
                                manga = manga,
                                isSelectionMode = state.selectionState.isSelectionMode,
                                isSelected = state.selectionState.selectedMangaIds.contains(manga.id),
                                onClick = {
                                    if (state.selectionState.isSelectionMode) {
                                        onToggleMangaSelection(manga.id)
                                    } else {
                                        onMangaSelected(manga.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// 1. Comfortable Card: cover-led desktop card with readable metadata
@Composable
private fun ComfortableMangaCard(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick)
            .focusable(),
        color = containerColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)) {
                MangaCover(
                    thumbnailUrl = manga.thumbnailUrl,
                    mangaId = manga.id,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                )

                if (manga.unreadCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(999.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Text(
                            text = strings.libraryUnreadCount(manga.unreadCount.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }

                if (isSelectionMode) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = CircleShape,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.testTag("item-select-${manga.id}"),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(manga.author?.takeIf { it.isNotBlank() } ?: "Source ${manga.sourceId}")
                        append(" · ${manga.chapterCount} ${chapterLabel(manga.chapterCount)}")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// 2. Compact Card: Aspect ratio cover with dark gradient title overlay
@Composable
private fun CompactMangaCard(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = if (isSelected) 6.dp else 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MangaCover(
                thumbnailUrl = manga.thumbnailUrl,
                mangaId = manga.id,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Top-right unread badge
            if (manga.unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Text(
                        text = manga.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Selection Checkbox
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0x88000000), shape = CircleShape),
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.testTag("item-select-${manga.id}"),
                    )
                }
            }
        }
    }
}

// 3. Cover Only Card: Seamless cover image matrix
@Composable
private fun CoverOnlyMangaCard(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isSelected) 6.dp else 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MangaCover(
                thumbnailUrl = manga.thumbnailUrl,
                mangaId = manga.id,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
            )

            // Top-right unread badge
            if (manga.unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Text(
                        text = manga.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            // Selection Checkbox
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0x88000000), shape = CircleShape),
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.testTag("item-select-${manga.id}"),
                    )
                }
            }
        }
    }
}

// 4. List Item: Full horizontal row
@Composable
private fun ListMangaItem(
    manga: LibraryManga,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-item-${manga.id}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.testTag("item-select-${manga.id}"),
                )
            }

            MangaCover(
                thumbnailUrl = manga.thumbnailUrl,
                mangaId = manga.id,
                contentDescription = manga.title,
                modifier = Modifier
                    .width(55.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = manga.author ?: "Source ${manga.sourceId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${manga.chapterCount} ${chapterLabel(manga.chapterCount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            if (manga.unreadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = strings.libraryUnreadCount(manga.unreadCount.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchCategorySelectionDialog(
    categories: List<DesktopCategory>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    val strings = LocalStrings.current
    var selectedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.libraryBatchChangeCategory) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (categories.isEmpty()) {
                    Text(strings.categoryNoneCreated)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategoryIds = if (selectedCategoryIds.contains(cat.id)) {
                                            selectedCategoryIds - cat.id
                                        } else {
                                            selectedCategoryIds + cat.id
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedCategoryIds.contains(cat.id),
                                    onCheckedChange = {
                                        selectedCategoryIds = if (it) {
                                            selectedCategoryIds + cat.id
                                        } else {
                                            selectedCategoryIds - cat.id
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategoryIds.toList()) },
                modifier = Modifier.testTag("batch-category-confirm"),
            ) {
                Text(strings.dialogDone)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.dialogCancel)
            }
        },
    )
}

@Composable
private fun EmptyState(query: String, modifier: Modifier) {
    val strings = LocalStrings.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (query.isBlank()) Icons.Rounded.CollectionsBookmark else Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (query.isBlank()) {
                Text(
                    strings.libraryEmptyTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.libraryEmptySubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    strings.libraryNoMatchTitle(query),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.libraryNoMatchSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val strings = LocalStrings.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.testTag("library-error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.testTag("library-retry"),
            ) {
                Text(strings.libraryRetry)
            }
        }
    }
}

private fun chapterLabel(count: Long): String = if (count == 1L) "chapter" else "chapters"
