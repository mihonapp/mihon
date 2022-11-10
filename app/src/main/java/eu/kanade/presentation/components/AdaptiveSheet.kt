package eu.kanade.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.isTabletUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val SheetAnimationDuration = 500
private val SheetAnimationSpec = tween<Float>(durationMillis = SheetAnimationDuration)
private const val ScrimAnimationDuration = 350
private val ScrimAnimationSpec = tween<Float>(durationMillis = ScrimAnimationDuration)

/**
 * Sheet with adaptive position aligned to bottom on small screen, otherwise aligned to center
 * and will not be able to dismissed with swipe gesture.
 *
 * Max width of the content is set to 460 dp.
 */
@Composable
fun AdaptiveSheet(
    tonalElevation: Dp = 1.dp,
    enableSwipeDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isTabletUi = isTabletUi()
    AdaptiveSheetImpl(
        isTabletUi = isTabletUi,
        tonalElevation = tonalElevation,
        enableSwipeDismiss = enableSwipeDismiss,
        onDismissRequest = onDismissRequest,
    ) {
        val contentPadding = if (isTabletUi) {
            PaddingValues()
        } else {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
        }
        content(contentPadding)
    }
}

@Composable
fun AdaptiveSheetImpl(
    isTabletUi: Boolean,
    tonalElevation: Dp,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (isTabletUi) {
        var targetAlpha by remember { mutableStateOf(0f) }
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = ScrimAnimationSpec,
        )
        val internalOnDismissRequest: () -> Unit = {
            scope.launch {
                targetAlpha = 0f
                delay(ScrimAnimationSpec.durationMillis.milliseconds)
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
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            )
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
                    BackHandler(onBack = internalOnDismissRequest)
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
            val scrimAlpha by animateFloatAsState(
                targetValue = if (swipeState.targetValue == 1) 0f else 1f,
                animationSpec = ScrimAnimationSpec,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(scrimAlpha)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            )
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
                shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
                tonalElevation = tonalElevation,
                content = {
                    BackHandler(onBack = internalOnDismissRequest)
                    content()
                },
            )

            LaunchedEffect(swipeState) {
                scope.launch { swipeState.animateTo(0) }
                snapshotFlow { swipeState.currentValue }
                    .drop(1)
                    .filter { it == 1 }
                    .collectLatest {
                        delay(ScrimAnimationSpec.durationMillis.milliseconds)
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
