package eu.kanade.presentation.library.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryNavigation
import eu.kanade.tachiyomi.ui.library.LibraryPageInfo
import kotlinx.coroutines.flow.filterNotNull
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.ceil

@Composable
internal fun LibraryPaginatedContent(
    items: List<LibraryItem>,
    navigation: LibraryNavigation,
    active: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    displayMode: LibraryDisplayMode,
    columns: Int,
    requestedRows: Int,
    onPageRowsChange: (Int) -> Unit,
    stretchPageSpacing: Boolean,
    contentPadding: PaddingValues,
    searchQuery: String?,
    hasActiveFilters: Boolean,
    onGlobalSearchClicked: () -> Unit,
    content: @Composable (List<LibraryItem>, Dp, Int) -> Unit,
) {
    val isList = displayMode == LibraryDisplayMode.List
    var anchorIndex by rememberSaveable(searchQuery, hasActiveFilters) { mutableIntStateOf(0) }
    var pageSize by remember(displayMode, columns, requestedRows) { mutableIntStateOf(0) }
    var rowsPerPage by remember { mutableIntStateOf(0) }
    var maximumRows by remember(displayMode, columns, requestedRows) { mutableIntStateOf(1) }
    var canStretchSpacing by remember(displayMode, columns, requestedRows) { mutableStateOf(false) }
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var maxItemWidth by remember(viewport, displayMode, columns, requestedRows, density) { mutableStateOf(Dp.Infinity) }
    val gridPadding = with(density) { 16.dp.roundToPx() }
    val columnSpacing = with(density) { CommonMangaItemDefaults.GridHorizontalSpacer.roundToPx() }
    val adaptiveColumnWidth = with(density) { 128.dp.roundToPx() }
    val itemPadding = with(density) { (CommonMangaItemDefaults.GridItemPadding * 2).roundToPx() }
    val chromeHeight = with(density) {
        itemPadding + if (displayMode == LibraryDisplayMode.ComfortableGrid) {
            (CommonMangaItemDefaults.ComfortableGridTitlePadding * 2).roundToPx() +
                ceil(CommonMangaItemDefaults.GridTitleLineHeight.toPx() * 2).toInt()
        } else {
            0
        }
    }
    val minimumCoverHeight = with(density) { 48.dp.roundToPx() }

    LaunchedEffect(viewport, displayMode, columns, requestedRows, density) {
        if (isList) listState.scrollToItem(0) else gridState.scrollToItem(0)
        snapshotFlow {
            if (isList) {
                val info = listState.layoutInfo
                val row = info.visibleItemsInfo.firstOrNull { it.contentType == "library_list_item" }
                row?.takeIf { info.viewportSize == viewport }?.let {
                    val height = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
                    val maximum = libraryPageCapacity(height, it.size, 0, 1, 0)
                    val rows = when {
                        requestedRows == 0 -> maximum
                        requestedRows < 0 -> maximum
                        else -> requestedRows.coerceAtMost(maximum)
                    }
                    LibraryPageLayout(rows, maximum, null, height - rows * it.size > 1, rows)
                }
            } else {
                val info = gridState.layoutInfo
                val row = info.visibleItemsInfo.firstOrNull()
                val columnCount = if (columns > 0) {
                    columns
                } else {
                    (
                        (info.viewportSize.width - gridPadding + columnSpacing) /
                            (adaptiveColumnWidth + columnSpacing)
                        ).coerceAtLeast(1)
                }
                row?.takeIf { info.viewportSize == viewport }?.let {
                    val height = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
                    libraryGridPageLayout(
                        height = height,
                        itemWidth = it.size.width,
                        columns = columnCount,
                        padding = itemPadding,
                        chromeHeight = chromeHeight,
                        spacing = info.mainAxisItemSpacing,
                        minimumCoverHeight = minimumCoverHeight,
                        requestedRows = requestedRows,
                    )
                }
            }
        }.filterNotNull().collect { layout ->
            pageSize = layout.capacity
            rowsPerPage = layout.rows
            maximumRows = layout.maxRows
            canStretchSpacing = layout.canStretchSpacing
            maxItemWidth = layout.itemWidth?.let { with(density) { it.toDp() } } ?: Dp.Infinity
        }
    }

    val size = pageSize.coerceAtLeast(1)
    fun currentPageStart(): Int {
        return libraryPageStart(anchorIndex, size, items.size)
    }
    val start = currentPageStart()
    val page = start / size
    val pageCount = (items.size - 1) / size + 1
    val onPageChange: (Int) -> Unit = { direction ->
        val next = libraryPageStart(currentPageStart() + direction * size, size, items.size)
        anchorIndex = next
    }
    SideEffect {
        if (active && pageSize > 0) {
            navigation.pageInfo = LibraryPageInfo(page, pageCount, maximumRows, canStretchSpacing)
            val compatibleRows = libraryCompatiblePageRows(requestedRows, maximumRows)
            if (compatibleRows != requestedRows) onPageRowsChange(compatibleRows)
        }
    }
    DisposableEffect(navigation, active) {
        onDispose {
            if (active) navigation.pageInfo = null
        }
    }

    val swipeThreshold = with(density) { 48.dp.toPx() }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (!searchQuery.isNullOrEmpty()) {
            GlobalSearchItem(searchQuery = searchQuery, onClick = onGlobalSearchClicked)
        }
        Box(
            Modifier.weight(1f)
                .onSizeChanged { viewport = it }
                .alpha(if (pageSize == 0) 0f else 1f)
                .pointerInput(swipeThreshold, size, items) {
                    var distance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { distance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            distance += amount
                        },
                        onDragEnd = {
                            if (abs(distance) >= swipeThreshold) {
                                onPageChange(if (distance < 0) 1 else -1)
                            }
                        },
                    )
                },
        ) {
            content(
                items.subList(start, (start + size).coerceAtMost(items.size)),
                maxItemWidth,
                if (stretchPageSpacing && canStretchSpacing) rowsPerPage else 0,
            )
        }
    }
}

@Composable
internal fun LibraryPageNumber(
    page: Int,
    pageCount: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(MR.strings.library_page_number, page + 1, pageCount)
    val textModifier = if (compact) Modifier.padding(horizontal = 8.dp, vertical = 4.dp) else modifier
    val number: @Composable () -> Unit = {
        Text(
            text = "${page + 1} / $pageCount",
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = textModifier.semantics { contentDescription = description },
        )
    }
    if (compact) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            number()
        }
    } else {
        number()
    }
}

internal fun libraryPageCapacity(height: Int, rowHeight: Int, spacing: Int, columns: Int, requestedRows: Int): Int {
    val fittingRows = ((height + spacing) / (rowHeight + spacing).coerceAtLeast(1)).coerceAtLeast(1)
    val rows = if (requestedRows > 0) requestedRows.coerceAtMost(fittingRows) else fittingRows
    return rows * columns.coerceAtLeast(1)
}

internal fun libraryPageStart(anchorIndex: Int, pageSize: Int, itemCount: Int): Int {
    val size = pageSize.coerceAtLeast(1)
    return anchorIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)) / size * size
}

// Only shrink oversized grid entries; retain their cover aspect ratio and measured title height.
internal fun libraryPageItemWidth(height: Int, rowHeight: Int, itemWidth: Int, padding: Int): Int {
    val coverHeight = (itemWidth - padding) * 3 / 2
    val titleHeight = rowHeight - coverHeight
    return ((height - titleHeight) * 2 / 3 + padding).coerceIn(padding + 1, itemWidth.coerceAtLeast(padding + 1))
}

internal data class LibraryPageLayout(
    val capacity: Int,
    val maxRows: Int,
    val itemWidth: Int?,
    val canStretchSpacing: Boolean,
    val rows: Int,
)

internal fun libraryGridPageLayout(
    height: Int,
    itemWidth: Int,
    columns: Int,
    padding: Int,
    chromeHeight: Int,
    spacing: Int,
    minimumCoverHeight: Int,
    requestedRows: Int,
): LibraryPageLayout {
    val coverHeight = (itemWidth - padding).coerceAtLeast(1) * 3 / 2
    val naturalHeight = coverHeight + chromeHeight
    val maximum = libraryPageCapacity(height, minOf(coverHeight, minimumCoverHeight) + chromeHeight, spacing, 1, 0)
    val rows = when {
        requestedRows == 0 -> libraryPageCapacity(height, naturalHeight, spacing, 1, 0)
        requestedRows < 0 -> maximum
        else -> requestedRows.coerceAtMost(maximum)
    }
    val rowHeight = ((height - spacing * (rows - 1)) / rows).coerceAtLeast(1)
    val width = if (naturalHeight >= rowHeight) {
        (libraryPageItemWidth(rowHeight, naturalHeight, itemWidth, padding) - 1).coerceAtLeast(padding + 1)
    } else {
        null
    }
    val canStretch = height - rows * naturalHeight - spacing * (rows - 1) > 1
    return LibraryPageLayout(rows * columns.coerceAtLeast(1), maximum, width, canStretch, rows)
}

internal fun libraryCompatiblePageRows(requestedRows: Int, maximum: Int): Int =
    if (requestedRows < 0) maximum else requestedRows.coerceAtMost(maximum)
