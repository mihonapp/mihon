package eu.kanade.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefreshState
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator as AccompanistSwipeRefreshIndicator

@Composable
fun SwipeRefreshIndicator(
    state: SwipeRefreshState,
    refreshTriggerDistance: Dp,
    refreshingOffset: Dp = 16.dp,
) {
    AccompanistSwipeRefreshIndicator(
        state = state,
        refreshTriggerDistance = refreshTriggerDistance,
        backgroundColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        refreshingOffset = refreshingOffset,
    )
}

@Composable
fun SwipeRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    com.google.accompanist.swiperefresh.SwipeRefresh(
        state = rememberSwipeRefreshState(refreshing),
        onRefresh = onRefresh,
        swipeEnabled = enabled,
        indicatorPadding = indicatorPadding,
        indicator = { s, trigger -> SwipeRefreshIndicator(s, trigger) },
    ) {
        content()
    }
}
