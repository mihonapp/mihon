package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import eu.kanade.presentation.category.visualName
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import kotlin.math.roundToInt

/**
 * Renders [items] as fixed, non-scrolling pages instead of one continuously
 * scrolling grid/list. Swiping moves between pages with no slide/fade
 * settle animation (an instant cut), similar to an e-reader page turn.
 *
 * Whatever the final row count ends up being (auto-fit or a manual
 * override), the rows are spread out with extra gap so they always span
 * the *entire* available height — instead of using a small fixed gap and
 * leaving a dead, unused gap at the bottom when fewer rows are requested
 * than would actually fit on screen.
 *
 * This intentionally does not support the "jump to global search" item
 * shown by the regular scrolling grid when a search query is active —
 * that item doesn't fit the fixed-page-size model. It simply isn't shown
 * while paged browsing is enabled.
 *
 * @param columns number of columns per page. Use 1 for a list layout.
 * @param manualRows when greater than 0, overrides the auto-fit row count
 *   with this exact number of rows per page (the "Items per column" slider).
 *   0 means auto: fit as many rows as the screen height allows.
 * @param cellHeightForWidth given the width a single cell will actually be
 *   rendered at (after dividing available width across [columns] and
 *   subtracting grid spacing), return that cell's real rendered height.
 *   This must reflect the same cover aspect ratio / title area used by
 *   [cell], otherwise rows will be mis-measured and either get cut off
 *   (estimate too short) or leave a gap (estimate too tall).
 */
@Composable
internal fun <T> PagedLibraryGrid(
    items: List<T>,
    columns: Int,
    manualRows: Int,
    contentPadding: PaddingValues,
    cellHeightForWidth: (cellWidth: Dp) -> Dp,
    cell: @Composable (T) -> Unit,
    categories: List<Category>,
    categoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    showHopper: Boolean,
    hopperOffsetX: Animatable<Float, *>,
    hopperInitialized: Boolean,
    onHopperInitialized: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSpacing = CommonMangaItemDefaults.GridHorizontalSpacer
    val minVerticalSpacing = CommonMangaItemDefaults.GridVerticalSpacer
    val gridPadding = 8.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableWidth = maxWidth -
            contentPadding.calculateStartPadding(layoutDirection) -
            contentPadding.calculateEndPadding(layoutDirection) -
            (gridPadding * 2)

        val availableHeight = maxHeight -
            contentPadding.calculateTopPadding() -
            contentPadding.calculateBottomPadding() -
            (gridPadding * 2)

        // 0 == Auto: fit as many columns as width allows (same 128.dp minimum as LazyLibraryGrid).
        val density = LocalDensity.current
        val safeColumns = if (columns == 0) {
            val availableWidthPx = with(density) { availableWidth.toPx() }
            val minCellPx = with(density) { (128.dp + horizontalSpacing).toPx() }
            (availableWidthPx / minCellPx).toInt().coerceAtLeast(1)
        } else {
            columns.coerceAtLeast(1)
        }

        // 1. Calculate the base cell width based on available horizontal space.
        val widthLimitedCellWidth = (availableWidth - (horizontalSpacing * (safeColumns - 1))) / safeColumns

        val finalCellWidth: Dp
        val finalCellHeight: Dp

        if (manualRows > 0) {
            // 2. If rows are forced, we must ensure they fit vertically.
            // We estimate the scaling factor by sampling the height function.
            val extraHeight = cellHeightForWidth(0.dp)
            val sampleWidth = 100.dp
            val sampleHeight = cellHeightForWidth(sampleWidth)
            val heightPerWidth = if (sampleWidth > 0.dp) (sampleHeight - extraHeight) / sampleWidth else 0f

            if (heightPerWidth > 0) {
                // Linear model: rowHeight = cellWidth * heightPerWidth + extraHeight
                // availableHeight >= manualRows * (w * hpw + extra) + (manualRows - 1) * minVerticalSpacing
                val maxHeightConstraint =
                    availableHeight - (extraHeight * manualRows) - (minVerticalSpacing * (manualRows - 1))
                val heightLimitedCellWidth = (maxHeightConstraint / (manualRows * heightPerWidth)).coerceAtLeast(0.dp)

                finalCellWidth =
                    if (heightLimitedCellWidth <
                        widthLimitedCellWidth
                    ) {
                        heightLimitedCellWidth
                    } else {
                        widthLimitedCellWidth
                    }
                finalCellHeight = cellHeightForWidth(finalCellWidth)
            } else {
                // Constant height layout (e.g. List)
                val maxPossibleHeight = (availableHeight - (minVerticalSpacing * (manualRows - 1))) / manualRows
                finalCellHeight = if (extraHeight > maxPossibleHeight) maxPossibleHeight else extraHeight
                finalCellWidth = widthLimitedCellWidth
            }
        } else {
            // Auto rows: use the full width available for columns.
            finalCellWidth = widthLimitedCellWidth
            finalCellHeight = cellHeightForWidth(finalCellWidth)
        }

        val rowsPerPage = if (manualRows > 0) {
            manualRows
        } else {
            ((availableHeight + minVerticalSpacing) / (finalCellHeight + minVerticalSpacing))
                .toInt()
                .coerceAtLeast(1)
        }

        // Spread whatever row count we ended up with across the full
        // available height: distribute any leftover space (after the rows
        // themselves) evenly as extra gap *between* rows.
        val effectiveVerticalSpacing = if (rowsPerPage > 1) {
            val leftoverForGaps = availableHeight - (finalCellHeight * rowsPerPage)
            max(minVerticalSpacing, leftoverForGaps / (rowsPerPage - 1))
        } else {
            minVerticalSpacing
        }

        val pageSize = (safeColumns * rowsPerPage).coerceAtLeast(1)
        val pages = remember(items, pageSize) { items.chunked(pageSize) }
        val pageCount = pages.size.coerceAtLeast(1)

        val pagerState = rememberPagerState { pageCount }
        val scope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                userScrollEnabled = false,
            ) { page ->
                val pageItems = pages.getOrElse(page) { emptyList() }
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(safeColumns),
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection) + gridPadding,
                        end = contentPadding.calculateEndPadding(layoutDirection) + gridPadding,
                        top = contentPadding.calculateTopPadding() + gridPadding,
                        bottom = contentPadding.calculateBottomPadding() + gridPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(effectiveVerticalSpacing),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                    userScrollEnabled = false,
                ) {
                    items(pageItems) { item ->
                        Box(
                            modifier = Modifier
                                .height(finalCellHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(modifier = Modifier.width(finalCellWidth)) {
                                cell(item)
                            }
                        }
                    }
                }
            }

            if (showHopper && (pageCount > 1 || categories.size > 1)) {
                CategoryHopper(
                    currentPage = pagerState.currentPage,
                    pageCount = pageCount,
                    categories = categories,
                    categoryIndex = categoryIndex,
                    onSelectCategory = onSelectCategory,
                    onPrevPage = {
                        scope.launch {
                            (pagerState.currentPage - 1).takeIf { it >= 0 }?.let {
                                pagerState.scrollToPage(it)
                            }
                        }
                    },
                    onNextPage = {
                        scope.launch {
                            (pagerState.currentPage + 1).takeIf { it < pageCount }?.let {
                                pagerState.scrollToPage(it)
                            }
                        }
                    },
                    containerWidth = this@BoxWithConstraints.maxWidth,
                    containerHeight = this@BoxWithConstraints.maxHeight,
                    contentPadding = contentPadding,
                    offsetX = hopperOffsetX,
                    initialized = hopperInitialized,
                    onInitialized = onHopperInitialized,
                )
            }
        }
    }
}

@Composable
private fun CategoryHopper(
    currentPage: Int,
    pageCount: Int,
    categories: List<Category>,
    categoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    containerWidth: Dp,
    containerHeight: Dp,
    contentPadding: PaddingValues,
    offsetX: Animatable<Float, *>,
    initialized: Boolean,
    onInitialized: () -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()

    // Bounds the hopper has to stay within, in pixels: the visible page
    // area minus the bar insets (so it can't be dragged behind the top or
    // bottom bars, or off either side of the screen).
    val boundsPx = with(density) {
        IntSize(containerWidth.roundToPx(), containerHeight.roundToPx())
    }
    val bottomInsetPx = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
    val startInsetPx = with(density) { contentPadding.calculateStartPadding(layoutDirection).roundToPx() }
    val endInsetPx = with(density) { contentPadding.calculateEndPadding(layoutDirection).roundToPx() }
    val marginPx = with(density) { 16.dp.roundToPx() }

    var hopperSizePx by remember { mutableStateOf(IntSize.Zero) }

    // offsetX and initialized are hoisted to LibraryPager level so the
    // position persists across category swipes — each new category page
    // reuses the same Animatable instead of creating a fresh one.

    fun leftX() = (startInsetPx + marginPx).toFloat()
    fun rightX() = (boundsPx.width - endInsetPx - hopperSizePx.width - marginPx).toFloat().coerceAtLeast(leftX())
    fun centerX() = ((boundsPx.width - hopperSizePx.width) / 2f).coerceIn(leftX(), rightX())
    fun nearestAnchor(x: Float): Float =
        listOf(leftX(), centerX(), rightX()).minByOrNull { kotlin.math.abs(it - x) } ?: x

    val restingY = (boundsPx.height - bottomInsetPx - hopperSizePx.height - marginPx).toFloat()

    // Start docked at the right, once we know the hopper's real size.
    LaunchedEffect(hopperSizePx) {
        if (!initialized && hopperSizePx != IntSize.Zero) {
            onInitialized()
            offsetX.snapTo(rightX())
        }
    }

    Surface(
        modifier = Modifier
            .onSizeChanged { hopperSizePx = it }
            .offset { IntOffset(offsetX.value.roundToInt(), restingY.roundToInt()) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch { offsetX.animateTo(nearestAnchor(offsetX.value)) }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(nearestAnchor(offsetX.value)) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // Reading/writing offsetX.value directly (not a
                    // separately-captured local val) is what makes this
                    // track live drag deltas correctly — this lambda is
                    // only ever created once (pointerInput key = Unit),
                    // so anything captured as a plain val here would be
                    // frozen at that first frame instead of reflecting
                    // the latest dragged position.
                    val newX = (offsetX.value + dragAmount).coerceIn(leftX(), rightX())
                    scope.launch { offsetX.snapTo(newX) }
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HopperIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous page",
                enabled = currentPage > 0,
                onClick = onPrevPage,
            )

            Box {
                HopperIconButton(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    contentDescription = "Jump to category",
                    enabled = true,
                    onClick = { expanded = true },
                )

                // Small page-count badge so the icon-based pill (matching
                // the reference design) doesn't lose the "page X of Y"
                // info entirely.
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Text(
                        text = "${currentPage + 1}/$pageCount",
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    categories.forEachIndexed { index, category ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = category.visualName,
                                    color = if (index == categoryIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            onClick = {
                                expanded = false
                                onSelectCategory(index)
                            },
                        )
                    }
                }
            }

            HopperIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next page",
                enabled = currentPage < pageCount - 1,
                onClick = onNextPage,
            )
        }
    }
}

/**
 * Matches Yokai's `rounded_category_hopper.xml` proportions: a 24dp icon
 * with 12dp padding on all sides (48dp total tap target), tinted to
 * contrast against the pill's [Surface] color.
 */
@Composable
private fun HopperIconButton(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(24.dp),
        )
    }
}
