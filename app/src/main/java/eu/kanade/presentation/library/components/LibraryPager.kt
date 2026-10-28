package eu.kanade.presentation.library.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryNavigation
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibraryPageButtons
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun LibraryPager(
    navigation: LibraryNavigation,
    paginated: Boolean,
    pageRows: Int,
    stretchPageSpacing: Boolean,
    onPageRowsChange: (Int) -> Unit,
    pageButtons: LibraryPageButtons,
    pageSlider: Boolean,
    state: PagerState,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    selection: Set<Long>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getCategoryForPage: (Int) -> Category,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onClickManga: (Category, LibraryManga) -> Unit,
    onLongClickManga: (Category, LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    HorizontalPager(
        modifier = Modifier.fillMaxSize(),
        state = state,
        key = { getCategoryForPage(it).id },
        verticalAlignment = Alignment.Top,
    ) { page ->
        if (page !in ((state.currentPage - 1)..(state.currentPage + 1))) {
            // To make sure only one offscreen page is being composed
            return@HorizontalPager
        }
        val category = getCategoryForPage(page)
        val items = getItemsForCategory(category)

        if (items.isEmpty()) {
            LibraryPagerEmptyScreen(
                searchQuery = searchQuery,
                hasActiveFilters = hasActiveFilters,
                contentPadding = contentPadding,
                onGlobalSearchClicked = onGlobalSearchClicked,
            )
            return@HorizontalPager
        }

        val displayMode by getDisplayMode(page)
        val columns by if (displayMode != LibraryDisplayMode.List) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            remember(isLandscape) { getColumnsForOrientation(isLandscape) }
        } else {
            remember { mutableIntStateOf(0) }
        }

        val onClickManga: (LibraryManga) -> Unit = { onClickManga(category, it) }
        val onLongClickManga: (LibraryManga) -> Unit = { onLongClickManga(category, it) }

        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        if (!paginated) {
            LibraryScrollNavigation(
                navigation = navigation,
                listState = listState,
                gridState = gridState,
                isList = displayMode == LibraryDisplayMode.List,
                active = state.currentPage == page,
            )
        }

        val content: @Composable (List<LibraryItem>, Dp, Int) -> Unit = {
                displayedItems,
                maxItemWidth,
                stretchRows,
            ->
            when (displayMode) {
                LibraryDisplayMode.List -> {
                    LibraryList(
                        stretchRows = stretchRows,
                        state = listState,
                        userScrollEnabled = !paginated,
                        items = displayedItems,
                        contentPadding = if (paginated) PaddingValues(0.dp) else contentPadding,
                        selection = selection,
                        onClick = onClickManga,
                        onLongClick = onLongClickManga,
                        onClickContinueReading = onClickContinueReading,
                        searchQuery = searchQuery.takeUnless { paginated },
                        onGlobalSearchClicked = onGlobalSearchClicked,
                    )
                }
                LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                    LibraryCompactGrid(
                        stretchRows = stretchRows,
                        stretchColumns = stretchRows > 0 && pageRows != 0,
                        maxItemWidth = maxItemWidth,
                        state = gridState,
                        userScrollEnabled = !paginated,
                        items = displayedItems,
                        showTitle = displayMode is LibraryDisplayMode.CompactGrid,
                        columns = columns,
                        contentPadding = if (paginated) PaddingValues(0.dp) else contentPadding,
                        selection = selection,
                        onClick = onClickManga,
                        onLongClick = onLongClickManga,
                        onClickContinueReading = onClickContinueReading,
                        searchQuery = searchQuery.takeUnless { paginated },
                        onGlobalSearchClicked = onGlobalSearchClicked,
                    )
                }
                LibraryDisplayMode.ComfortableGrid -> {
                    LibraryComfortableGrid(
                        stretchRows = stretchRows,
                        stretchColumns = stretchRows > 0 && pageRows != 0,
                        maxItemWidth = maxItemWidth,
                        state = gridState,
                        userScrollEnabled = !paginated,
                        items = displayedItems,
                        columns = columns,
                        contentPadding = if (paginated) PaddingValues(0.dp) else contentPadding,
                        selection = selection,
                        onClick = onClickManga,
                        onLongClick = onLongClickManga,
                        onClickContinueReading = onClickContinueReading,
                        searchQuery = searchQuery.takeUnless { paginated },
                        onGlobalSearchClicked = onGlobalSearchClicked,
                    )
                }
            }
        }
        if (paginated) {
            LibraryPaginatedContent(
                items = items,
                navigation = navigation,
                active = state.currentPage == page,
                listState = listState,
                gridState = gridState,
                displayMode = displayMode,
                columns = columns,
                requestedRows = pageRows,
                onPageRowsChange = onPageRowsChange,
                stretchPageSpacing = stretchPageSpacing,
                buttons = pageButtons,
                showSlider = pageSlider,
                contentPadding = contentPadding,
                searchQuery = searchQuery,
                hasActiveFilters = hasActiveFilters,
                onGlobalSearchClicked = onGlobalSearchClicked,
                content = content,
            )
        } else {
            content(items, Dp.Infinity, 0)
        }
    }
}

@Composable
private fun LibraryPagerEmptyScreen(
    searchQuery: String?,
    hasActiveFilters: Boolean,
    contentPadding: PaddingValues,
    onGlobalSearchClicked: () -> Unit,
) {
    val msg = when {
        !searchQuery.isNullOrEmpty() -> MR.strings.no_results_found
        hasActiveFilters -> MR.strings.error_no_match
        else -> MR.strings.information_no_manga_category
    }

    Column(
        modifier = Modifier
            .padding(contentPadding + PaddingValues(8.dp))
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!searchQuery.isNullOrEmpty()) {
            GlobalSearchItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }

        EmptyScreen(
            stringRes = msg,
            modifier = Modifier.weight(1f),
        )
    }
}
