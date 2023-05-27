package tachiyomi.presentation.core.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val SheetAnimationDuration = 350
private val SheetAnimationSpec = tween<Float>(durationMillis = SheetAnimationDuration)

@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    tonalElevation: Dp,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (isTabletUi) {
        var targetAlpha by remember { mutableFloatStateOf(0f) }
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = SheetAnimationSpec,
        )
        val internalOnDismissRequest: () -> Unit = {
            scope.launch {
                targetAlpha = 0f
                onDismissRequest()
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .clickable(
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .requiredWidthIn(max = 460.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .systemBarsPadding()
                    .padding(vertical = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = tonalElevation,
                content = {
                    BackHandler(enabled = alpha > 0f, onBack = internalOnDismissRequest)
                    content()
                },
            )

            LaunchedEffect(Unit) {
                targetAlpha = 1f
            }
        }
    } else {
        val swipeState = rememberSwipeableState(
            initialValue = 1,
            animationSpec = SheetAnimationSpec,
        )
        val internalOnDismissRequest: () -> Unit = { if (swipeState.currentValue == 0) scope.launch { swipeState.animateTo(1) } }
        BoxWithConstraints(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val fullHeight = constraints.maxHeight.toFloat()
            val anchors = mapOf(0f to 0, fullHeight to 1)
            Surface(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .nestedScroll(
                        remember(enableSwipeDismiss, anchors) {
                            swipeState.preUpPostDownNestedScrollConnection(
                                enabled = enableSwipeDismiss,
                                anchor = anchors,
                            )
                        },
                    )
                    .offset {
                        IntOffset(
                            0,
                            swipeState.offset.value.roundToInt(),
                        )
                    }
                    .swipeable(
                        enabled = enableSwipeDismiss,
                        state = swipeState,
                        anchors = anchors,
                        orientation = Orientation.Vertical,
                        resistance = null,
                    )
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = tonalElevation,
                content = {
                    BackHandler(enabled = swipeState.targetValue == 0, onBack = internalOnDismissRequest)
                    content()
                },
            )

            LaunchedEffect(swipeState) {
                scope.launch { swipeState.animateTo(0) }
                snapshotFlow { swipeState.currentValue }
                    .drop(1)
                    .filter { it == 1 }
                    .collectLatest {
                        onDismissRequest()
                    }
            }
        }
    }
}

/**
 * Yoinked from Swipeable.kt with modifications to disable
 */
private fun <T> SwipeableState<T>.preUpPostDownNestedScrollConnection(
    enabled: Boolean = true,
    anchor: Map<Float, T>,
) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.toFloat()
        return if (enabled && delta < 0 && source == NestedScrollSource.Drag) {
            performDrag(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (enabled && source == NestedScrollSource.Drag) {
            performDrag(available.toFloat()).toOffset()
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = Offset(available.x, available.y).toFloat()
        return if (enabled && toFling < 0 && offset.value > anchor.keys.minOrNull()!!) {
            performFling(velocity = toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return if (enabled) {
            performFling(velocity = Offset(available.x, available.y).toFloat())
            available
        } else {
            Velocity.Zero
        }
    }

    private fun Float.toOffset(): Offset = Offset(0f, this)

    private fun Offset.toFloat(): Float = this.y
}
