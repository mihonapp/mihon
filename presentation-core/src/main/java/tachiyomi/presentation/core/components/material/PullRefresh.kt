package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

/**
 * @param refreshing Whether the layout is currently refreshing
 * @param onRefresh Lambda which is invoked when a swipe to refresh gesture is completed.
 * @param enabled Whether the the layout should react to swipe gestures or not.
 * @param indicatorPadding Content padding for the indicator, to inset the indicator in if required.
 * @param content The content containing a vertically scrollable composable.
 *
 * Code reference: [Accompanist SwipeRefresh](https://github.com/google/accompanist/blob/677bc4ca0ee74677a8ba73793d04d85fe4ab55fb/swiperefresh/src/main/java/com/google/accompanist/swiperefresh/SwipeRefresh.kt#L265-L283)
 */
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val state = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onRefresh,
    )

    Box(Modifier.pullRefresh(state, enabled)) {
        content()

        Box(
            Modifier
                .padding(indicatorPadding)
                .matchParentSize()
                .clipToBounds(),
        ) {
            PullRefreshIndicator(
                refreshing = refreshing,
                state = state,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
