package tachiyomi.presentation.core.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

@Composable
fun LazyListState.shouldExpandFAB(): Boolean {
    return remember {
        derivedStateOf {
            (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) ||
                lastScrolledBackward ||
                !canScrollForward
        }
    }
        .value
}
