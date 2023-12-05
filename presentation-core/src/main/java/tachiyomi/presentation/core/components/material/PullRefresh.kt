package tachiyomi.presentation.core.components.material

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow

/**
 * @param refreshing Whether the layout is currently refreshing
 * @param onRefresh Lambda which is invoked when a swipe to refresh gesture is completed.
 * @param enabled Whether the the layout should react to swipe gestures or not.
 * @param indicatorPadding Content padding for the indicator, to inset the indicator in if required.
 * @param content The content containing a vertically scrollable composable.
 */
@Composable
fun PullRefresh(
    refreshing: Boolean,
    enabled: () -> Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState(
        isRefreshing = refreshing,
        extraVerticalOffset = indicatorPadding.calculateTopPadding(),
        enabled = enabled,
        onRefresh = onRefresh,
    )

    Box(modifier.nestedScroll(state.nestedScrollConnection)) {
        content()

        val contentPadding = remember(indicatorPadding) {
            object : PaddingValues {
                override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                    indicatorPadding.calculateLeftPadding(layoutDirection)

                override fun calculateTopPadding(): Dp = 0.dp

                override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                    indicatorPadding.calculateRightPadding(layoutDirection)

                override fun calculateBottomPadding(): Dp =
                    indicatorPadding.calculateBottomPadding()
            }
        }
        PullToRefreshContainer(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(contentPadding),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberPullToRefreshState(
    isRefreshing: Boolean,
    extraVerticalOffset: Dp,
    positionalThreshold: Dp = 64.dp,
    enabled: () -> Boolean = { true },
    onRefresh: () -> Unit,
): PullToRefreshStateImpl {
    val density = LocalDensity.current
    val extraVerticalOffsetPx = with(density) { extraVerticalOffset.toPx() }
    val positionalThresholdPx = with(density) { positionalThreshold.toPx() }
    return rememberSaveable(
        extraVerticalOffset,
        positionalThresholdPx,
        enabled,
        onRefresh,
        saver = PullToRefreshStateImpl.Saver(
            extraVerticalOffset = extraVerticalOffsetPx,
            positionalThreshold = positionalThresholdPx,
            enabled = enabled,
            onRefresh = onRefresh,
        ),
    ) {
        PullToRefreshStateImpl(
            initialRefreshing = isRefreshing,
            extraVerticalOffset = extraVerticalOffsetPx,
            positionalThreshold = positionalThresholdPx,
            enabled = enabled,
            onRefresh = onRefresh,
        )
    }.also {
        LaunchedEffect(isRefreshing) {
            if (isRefreshing && !it.isRefreshing) {
                it.startRefreshAnimated()
            } else if (!isRefreshing && it.isRefreshing) {
                it.endRefreshAnimated()
            }
        }
    }
}

/**
 * Creates a [PullToRefreshState].
 *
 * @param positionalThreshold The positional threshold, in pixels, in which a refresh is triggered
 * @param extraVerticalOffset Extra vertical offset, in pixels, for the "refreshing" state
 * @param initialRefreshing The initial refreshing value of [PullToRefreshState]
 * @param enabled a callback used to determine whether scroll events are to be handled by this
 * @param onRefresh a callback to run when pull-to-refresh action is triggered by user
 * [PullToRefreshState]
 */
private class PullToRefreshStateImpl(
    initialRefreshing: Boolean,
    private val extraVerticalOffset: Float,
    override val positionalThreshold: Float,
    enabled: () -> Boolean,
    private val onRefresh: () -> Unit,
) : PullToRefreshState {

    override val progress get() = adjustedDistancePulled / positionalThreshold
    override var verticalOffset by mutableFloatStateOf(if (initialRefreshing) refreshingVerticalOffset else 0f)

    override var isRefreshing by mutableStateOf(initialRefreshing)

    private val refreshingVerticalOffset: Float
        get() = positionalThreshold + extraVerticalOffset

    override fun startRefresh() {
        isRefreshing = true
        verticalOffset = refreshingVerticalOffset
    }

    suspend fun startRefreshAnimated() {
        isRefreshing = true
        animateTo(refreshingVerticalOffset)
    }

    override fun endRefresh() {
        verticalOffset = 0f
        isRefreshing = false
    }

    suspend fun endRefreshAnimated() {
        animateTo(0f)
        isRefreshing = false
    }

    override var nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset = when {
            !enabled() -> Offset.Zero
            // Swiping up
            source == NestedScrollSource.Drag && available.y < 0 -> {
                consumeAvailableOffset(available)
            }
            else -> Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = when {
            !enabled() -> Offset.Zero
            // Swiping down
            source == NestedScrollSource.Drag && available.y > 0 -> {
                consumeAvailableOffset(available)
            }
            else -> Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return Velocity(0f, onRelease(available.y))
        }
    }

    /** Helper method for nested scroll connection */
    fun consumeAvailableOffset(available: Offset): Offset {
        val y = if (isRefreshing) {
            0f
        } else {
            val newOffset = (distancePulled + available.y).coerceAtLeast(0f)
            val dragConsumed = newOffset - distancePulled
            distancePulled = newOffset
            verticalOffset = calculateVerticalOffset() + (extraVerticalOffset * progress.coerceIn(0f, 1f))
            dragConsumed
        }
        return Offset(0f, y)
    }

    /** Helper method for nested scroll connection. Calls onRefresh callback when triggered */
    suspend fun onRelease(velocity: Float): Float {
        if (isRefreshing) return 0f // Already refreshing, do nothing
        // Trigger refresh
        if (adjustedDistancePulled > positionalThreshold) {
            onRefresh()
            startRefreshAnimated()
        } else {
            animateTo(0f)
        }

        val consumed = when {
            // We are flinging without having dragged the pull refresh (for example a fling inside
            // a list) - don't consume
            distancePulled == 0f -> 0f
            // If the velocity is negative, the fling is upwards, and we don't want to prevent the
            // the list from scrolling
            velocity < 0f -> 0f
            // We are showing the indicator, and the fling is downwards - consume everything
            else -> velocity
        }
        distancePulled = 0f
        return consumed
    }

    suspend fun animateTo(offset: Float) {
        animate(initialValue = verticalOffset, targetValue = offset) { value, _ ->
            verticalOffset = value
        }
    }

    /** Provides custom vertical offset behavior for [PullToRefreshContainer] */
    fun calculateVerticalOffset(): Float = when {
        // If drag hasn't gone past the threshold, the position is the adjustedDistancePulled.
        adjustedDistancePulled <= positionalThreshold -> adjustedDistancePulled
        else -> {
            // How far beyond the threshold pull has gone, as a percentage of the threshold.
            val overshootPercent = abs(progress) - 1.0f
            // Limit the overshoot to 200%. Linear between 0 and 200.
            val linearTension = overshootPercent.coerceIn(0f, 2f)
            // Non-linear tension. Increases with linearTension, but at a decreasing rate.
            val tensionPercent = linearTension - linearTension.pow(2) / 4
            // The additional offset beyond the threshold.
            val extraOffset = positionalThreshold * tensionPercent
            positionalThreshold + extraOffset
        }
    }

    companion object {
        /** The default [Saver] for [PullToRefreshStateImpl]. */
        fun Saver(
            extraVerticalOffset: Float,
            positionalThreshold: Float,
            enabled: () -> Boolean,
            onRefresh: () -> Unit,
        ) = Saver<PullToRefreshStateImpl, Boolean>(
            save = { it.isRefreshing },
            restore = { isRefreshing ->
                PullToRefreshStateImpl(
                    initialRefreshing = isRefreshing,
                    extraVerticalOffset = extraVerticalOffset,
                    positionalThreshold = positionalThreshold,
                    enabled = enabled,
                    onRefresh = onRefresh,
                )
            },
        )
    }

    private var distancePulled by mutableFloatStateOf(0f)
    private val adjustedDistancePulled: Float get() = distancePulled * 0.5f
}
