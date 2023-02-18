package tachiyomi.presentation.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.presentation.core.components.material.padding
import java.text.DateFormatSymbols
import java.time.LocalDate
import kotlin.math.absoluteValue

@Composable
fun WheelPicker(
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    count: Int,
    size: DpSize = DpSize(128.dp, 128.dp),
    onSelectionChanged: (index: Int) -> Unit = {},
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
    itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
) {
    val lazyListState = rememberLazyListState(startIndex)

    LaunchedEffect(lazyListState, onSelectionChanged) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .map { calculateSnappedItemIndex(lazyListState) }
            .distinctUntilChanged()
            .collectLatest {
                onSelectionChanged(it)
            }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        backgroundContent?.invoke(size)

        LazyColumn(
            modifier = Modifier
                .height(size.height)
                .width(size.width),
            state = lazyListState,
            contentPadding = PaddingValues(vertical = size.height / RowCount * ((RowCount - 1) / 2)),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
        ) {
            items(count) { index ->
                Box(
                    modifier = Modifier
                        .height(size.height / RowCount)
                        .width(size.width)
                        .alpha(
                            calculateAnimatedAlpha(
                                lazyListState = lazyListState,
                                index = index,
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    itemContent(index)
                }
            }
        }
    }
}

@Composable
fun WheelTextPicker(
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    texts: List<String>,
    size: DpSize = DpSize(128.dp, 128.dp),
    onSelectionChanged: (index: Int) -> Unit = {},
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
) {
    WheelPicker(
        modifier = modifier,
        startIndex = startIndex,
        count = remember(texts) { texts.size },
        size = size,
        onSelectionChanged = onSelectionChanged,
        backgroundContent = backgroundContent,
    ) {
        WheelPickerDefaults.Item(text = texts[it])
    }
}

@Composable
fun WheelDatePicker(
    modifier: Modifier = Modifier,
    startDate: LocalDate = LocalDate.now(),
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    size: DpSize = DpSize(256.dp, 128.dp),
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
    onSelectionChanged: (date: LocalDate) -> Unit = {},
) {
    var internalSelection by remember { mutableStateOf(startDate) }
    val internalOnSelectionChange: (LocalDate) -> Unit = {
        internalSelection = it
        onSelectionChanged(internalSelection)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        backgroundContent?.invoke(size)
        Row {
            val singularPickerSize = DpSize(
                width = size.width / 3,
                height = size.height,
            )

            // Day
            val dayOfMonths = remember(internalSelection, minDate, maxDate) {
                if (minDate == null && maxDate == null) {
                    1..internalSelection.lengthOfMonth()
                } else {
                    val minDay = if (minDate?.month == internalSelection.month &&
                        minDate?.year == internalSelection.year
                    ) {
                        minDate.dayOfMonth
                    } else {
                        1
                    }
                    val maxDay = if (maxDate?.month == internalSelection.month &&
                        maxDate?.year == internalSelection.year
                    ) {
                        maxDate.dayOfMonth
                    } else {
                        31
                    }
                    minDay..maxDay.coerceAtMost(internalSelection.lengthOfMonth())
                }.toList()
            }
            WheelTextPicker(
                size = singularPickerSize,
                texts = dayOfMonths.map { it.toString() },
                backgroundContent = null,
                startIndex = dayOfMonths.indexOfFirst { it == startDate.dayOfMonth }.coerceAtLeast(0),
                onSelectionChanged = { index ->
                    val newDayOfMonth = dayOfMonths[index]
                    internalOnSelectionChange(internalSelection.withDayOfMonth(newDayOfMonth))
                },
            )

            // Month
            val months = remember(internalSelection, minDate, maxDate) {
                val monthRange = if (minDate == null && maxDate == null) {
                    1..12
                } else {
                    val minMonth = if (minDate?.year == internalSelection.year) {
                        minDate.monthValue
                    } else {
                        1
                    }
                    val maxMonth = if (maxDate?.year == internalSelection.year) {
                        maxDate.monthValue
                    } else {
                        12
                    }
                    minMonth..maxMonth
                }
                val dateFormatSymbols = DateFormatSymbols()
                monthRange.map { it to dateFormatSymbols.months[it - 1] }
            }
            WheelTextPicker(
                size = singularPickerSize,
                texts = months.map { it.second },
                backgroundContent = null,
                startIndex = months.indexOfFirst { it.first == startDate.monthValue }.coerceAtLeast(0),
                onSelectionChanged = { index ->
                    val newMonth = months[index].first
                    internalOnSelectionChange(internalSelection.withMonth(newMonth))
                },
            )

            // Year
            val years = remember(minDate, maxDate) {
                val minYear = minDate?.year?.coerceAtLeast(1900) ?: 1900
                val maxYear = maxDate?.year?.coerceAtMost(2100) ?: 2100
                val yearRange = minYear..maxYear
                yearRange.toList()
            }
            WheelTextPicker(
                size = singularPickerSize,
                texts = years.map { it.toString() },
                backgroundContent = null,
                startIndex = years.indexOfFirst { it == startDate.year }.coerceAtLeast(0),
                onSelectionChanged = { index ->
                    val newYear = years[index]
                    internalOnSelectionChange(internalSelection.withYear(newYear))
                },
            )
        }
    }
}

private fun LazyListState.snapOffsetForItem(itemInfo: LazyListItemInfo): Int {
    val startScrollOffset = 0
    val endScrollOffset = layoutInfo.let { it.viewportEndOffset - it.afterContentPadding }
    return startScrollOffset + (endScrollOffset - startScrollOffset - itemInfo.size) / 2
}

private fun LazyListState.distanceToSnapForIndex(index: Int): Int {
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (itemInfo != null) {
        return itemInfo.offset - snapOffsetForItem(itemInfo)
    }
    return 0
}

private fun calculateAnimatedAlpha(
    lazyListState: LazyListState,
    index: Int,
): Float {
    val distanceToIndexSnap = lazyListState.distanceToSnapForIndex(index).absoluteValue
    val viewPortHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()
    val singleViewPortHeight = viewPortHeight / RowCount
    return if (distanceToIndexSnap in 0..singleViewPortHeight.toInt()) {
        1.2f - (distanceToIndexSnap / singleViewPortHeight)
    } else {
        0.2f
    }
}

private fun calculateSnappedItemIndex(lazyListState: LazyListState): Int {
    return lazyListState.layoutInfo.visibleItemsInfo
        .maxBy { calculateAnimatedAlpha(lazyListState, it.index) }
        .index
}

object WheelPickerDefaults {
    @Composable
    fun Background(size: DpSize) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .size(size.width, size.height / RowCount),
            shape = RoundedCornerShape(MaterialTheme.padding.medium),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            content = {},
        )
    }

    @Composable
    fun Item(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

private const val RowCount = 3
