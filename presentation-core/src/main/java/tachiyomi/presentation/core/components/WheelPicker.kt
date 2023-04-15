package tachiyomi.presentation.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.clearFocusOnSoftKeyboardHide
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.showSoftKeyboard
import kotlin.math.absoluteValue

@Composable
fun WheelNumberPicker(
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    items: List<Number>,
    size: DpSize = DpSize(128.dp, 128.dp),
    onSelectionChanged: (index: Int) -> Unit = {},
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
) {
    WheelPicker(
        modifier = modifier,
        startIndex = startIndex,
        items = items,
        size = size,
        onSelectionChanged = onSelectionChanged,
        manualInputType = KeyboardType.Number,
        backgroundContent = backgroundContent,
    ) {
        WheelPickerDefaults.Item(text = "$it")
    }
}

@Composable
fun WheelTextPicker(
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    items: List<String>,
    size: DpSize = DpSize(128.dp, 128.dp),
    onSelectionChanged: (index: Int) -> Unit = {},
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
) {
    WheelPicker(
        modifier = modifier,
        startIndex = startIndex,
        items = items,
        size = size,
        onSelectionChanged = onSelectionChanged,
        backgroundContent = backgroundContent,
    ) {
        WheelPickerDefaults.Item(text = it)
    }
}

@Composable
private fun <T> WheelPicker(
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    items: List<T>,
    size: DpSize = DpSize(128.dp, 128.dp),
    onSelectionChanged: (index: Int) -> Unit = {},
    manualInputType: KeyboardType? = null,
    backgroundContent: (@Composable (size: DpSize) -> Unit)? = {
        WheelPickerDefaults.Background(size = it)
    },
    itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState(startIndex)

    var internalIndex by remember { mutableStateOf(startIndex) }
    val internalOnSelectionChanged: (Int) -> Unit = {
        internalIndex = it
        onSelectionChanged(it)
    }

    LaunchedEffect(lazyListState, onSelectionChanged) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .map { calculateSnappedItemIndex(lazyListState) }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                internalOnSelectionChanged(it)
            }
    }

    Box(
        modifier = modifier
            .height(size.height)
            .width(size.width),
        contentAlignment = Alignment.Center,
    ) {
        backgroundContent?.invoke(size)

        var showManualInput by remember { mutableStateOf(false) }
        if (showManualInput) {
            var value by remember {
                val currentString = items[internalIndex].toString()
                mutableStateOf(TextFieldValue(text = currentString, selection = TextRange(currentString.length)))
            }

            val scope = rememberCoroutineScope()
            BasicTextField(
                modifier = Modifier
                    .align(Alignment.Center)
                    .showSoftKeyboard(true)
                    .clearFocusOnSoftKeyboardHide {
                        scope.launch {
                            items
                                .indexOfFirst { it.toString() == value.text }
                                .takeIf { it >= 0 }
                                ?.apply {
                                    internalOnSelectionChanged(this)
                                    lazyListState.scrollToItem(this)
                                }

                            showManualInput = false
                        }
                    },
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = manualInputType!!,
                    imeAction = ImeAction.Done,
                ),
                textStyle = MaterialTheme.typography.titleMedium +
                    TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .let {
                        if (manualInputType != null) {
                            it.clickableNoIndication { showManualInput = true }
                        } else {
                            it
                        }
                    },
                state = lazyListState,
                contentPadding = PaddingValues(vertical = size.height / RowCount * ((RowCount - 1) / 2)),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
            ) {
                itemsIndexed(items) { index, item ->
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
                        itemContent(item)
                    }
                }
            }
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
