package eu.kanade.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.google.accompanist.swiperefresh.SwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator as AccompanistSwipeRefreshIndicator

@Composable
fun SwipeRefreshIndicator(state: SwipeRefreshState, refreshTrigger: Dp) {
    AccompanistSwipeRefreshIndicator(
        state = state,
        refreshTriggerDistance = refreshTrigger,
        backgroundColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}
