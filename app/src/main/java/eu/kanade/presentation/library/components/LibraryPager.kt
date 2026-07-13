package eu.kanade.presentation.library.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun LibraryPager(
    state: PagerState,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    pagedBrowsing: Boolean,
    selection: Set<Long>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getCategoryForPage: (Int) -> Category,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getRowsForPagedBrowsing: () -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onClickManga: (Category, LibraryManga) -> Unit,
    onLongClickManga: (Category, LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    categories: List<Category>,
    onSelectCategory: (Int) -> Unit,
    showHopper: Boolean,
    pageTranslationX: Animatable<Float, *>,
    modifier: Modifier = Modifier,
) {
    var containerHeight by remember { mutableIntStateOf(0) }

    // Hoisted here so the hopper position survives category page swaps.
    // If these lived inside CategoryHopper (or PagedLibraryGrid), each new
    // category page would create fresh state and reset the position.
    val hopperOffsetX = remember { Animatable(0f) }
    var hopperInitialized by remember { mutableStateOf(false) }

    HorizontalPager(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { layoutCoordinates ->
                containerHeight = layoutCoordinates.size.height
            },
        state = state,
        verticalAlignment = Alignment.Top,
        // Disable the pager's own drag-to-scroll — we intercept horizontal
        // swipes ourselves in LibraryContent with a fixed-threshold
        // detector that calls scrollToPage() for an instant cut, matching
        // Yokai's snappy category switching behavior.
        userScrollEnabled = false,
    ) { page ->
        if (page !in ((state.currentPage - 1)..(state.currentPage + 1))) {
            return@HorizontalPager
        }

        // Apply the rubber-band translationX only to the current page —
        // adjacent pages stay in place since they're invisible during the
        // gesture anyway (userScrollEnabled = false).
        val tx = if (page == state.currentPage) pageTranslationX else null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (tx != null) {
                        Modifier.graphicsLayer { translationX = tx.value }
                    } else {
                        Modifier
                    },
                ),
        ) {
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
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val columns by remember(isLandscape) { getColumnsForOrientation(isLandscape) }
            val onClickManga: (LibraryManga) -> Unit = { onClickManga(category, it) }
            val onLongClickManga: (LibraryManga) -> Unit = { onLongClickManga(category, it) }
            val manualRows by remember { getRowsForPagedBrowsing() }

            when (displayMode) {
                LibraryDisplayMode.List -> {
                    LibraryList(
                        items = items,
                        entries = columns,
                        containerHeight = containerHeight,
                        pagedBrowsing = pagedBrowsing,
                        manualRows = manualRows,
                        contentPadding = contentPadding,
                        selection = selection,
                        onClick = onClickManga,
                        onLongClick = onLongClickManga,
                        onClickContinueReading = onClickContinueReading,
                        searchQuery = searchQuery,
                        onGlobalSearchClicked = onGlobalSearchClicked,
                        categories = categories,
                        categoryIndex = page,
                        onSelectCategory = onSelectCategory,
                        showHopper = showHopper,
                        hopperOffsetX = hopperOffsetX,
                        hopperInitialized = hopperInitialized,
                        onHopperInitialized = { hopperInitialized = true },
                    )
                }
                LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                    LibraryCompactGrid(
                        items = items,
                        showTitle = displayMode is LibraryDisplayMode.CompactGrid,
                        columns = columns,
                        pagedBrowsing = pagedBrowsing,
                        manualRows = manualRows,
                        contentPadding = contentPadding,
                        selection = selection,
                        onClick = onClickManga,
                        onLongClick = onLongClickManga,
                        onClickContinueReading = onClickContinueReading,
                        searchQuery = searchQuery,
                        onGlobalSearchClicked = onGlobalSearchClicked,
                        categories = categories,
                        categoryIndex = page,
                        onSelectCategory = onSelectCategory,
                        showHopper = showHopper,
                        hopperOffsetX = hopperOffsetX,
                        hopperInitialized = hopperInitialized,
                        onHopperInitialized = { hopperInitialized = true },
                    )
                }
                LibraryDisplayMode.ComfortableGrid -> {
                    LibraryComfortableGrid(
                        items = items,
                        columns = columns,
                        pagedBrowsing = pagedBrowsing,
                        manualRows = manualRows,
                        contentPadding = contentPadding,
                        selection = selection,
                        onClick = onClickManga,
                        onLongClick = onLongClickManga,
                        onClickContinueReading = onClickContinueReading,
                        searchQuery = searchQuery,
                        onGlobalSearchClicked = onGlobalSearchClicked,
                        categories = categories,
                        categoryIndex = page,
                        onSelectCategory = onSelectCategory,
                        showHopper = showHopper,
                        hopperOffsetX = hopperOffsetX,
                        hopperInitialized = hopperInitialized,
                        onHopperInitialized = { hopperInitialized = true },
                    )
                }
            } // end when
        } // end Box (rubber-band wrapper)
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
