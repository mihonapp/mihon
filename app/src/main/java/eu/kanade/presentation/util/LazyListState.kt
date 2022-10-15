package eu.kanade.presentation.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun LazyListState.isScrolledToStart(): Boolean {
    return remember {
        derivedStateOf {
            val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstItem == null || firstItem.offset == layoutInfo.viewportStartOffset
        }
    }.value
}

@Composable
fun LazyListState.isScrolledToEnd(): Boolean {
    return remember {
        derivedStateOf {
            val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastItem == null || lastItem.size + lastItem.offset <= layoutInfo.viewportEndOffset
        }
    }.value
}

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember { mutableStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableStateOf(firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

@Composable
fun LazyListState.isScrollingDown(): Boolean {
    var previousIndex by remember { mutableStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableStateOf(firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex < firstVisibleItemIndex
            } else {
                previousScrollOffset <= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}
