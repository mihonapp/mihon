package tachiyomi.presentation.core.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.util.PredictiveBack
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    if (isTabletUi) {
        var targetAlpha by remember { mutableFloatStateOf(0f) }
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = sheetAnimationSpec,
            label = "alpha",
        )
        val internalOnDismissRequest: () -> Unit = {
            scope.launch {
                targetAlpha = 0f
                onDismissRequest()
            }
        }
        Box(
            modifier = Modifier
                .clickable(
                    enabled = true,
                    interactionSource = null,
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .predictiveBackAnimation(
                        enabled = remember { derivedStateOf { alpha > 0f } }.value,
                        transformOrigin = TransformOrigin.Center,
                        onBack = internalOnDismissRequest,
                    )
                    .requiredWidthIn(max = 460.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    )
                    .systemBarsPadding()
                    .padding(vertical = 16.dp)
                    .then(modifier),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    content()
                },
            )

            LaunchedEffect(Unit) {
                targetAlpha = 1f
            }
        }
    } else {
        val anchoredDraggableState = rememberSaveable(saver = AnchoredDraggableState.Saver()) {
            AnchoredDraggableState(initialValue = 1)
        }
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = anchoredDraggableState,
            positionalThreshold = { _: Float -> with(density) { 56.dp.toPx() } },
            animationSpec = sheetAnimationSpec,
        )
        val internalOnDismissRequest = {
            if (anchoredDraggableState.settledValue == 0) {
                scope.launch { anchoredDraggableState.animateTo(1) }
            }
        }
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize()
                .onSizeChanged {
                    val anchors = DraggableAnchors {
                        0 at 0f
                        1 at it.height.toFloat()
                    }
                    anchoredDraggableState.updateAnchors(anchors)
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .predictiveBackAnimation(
                        enabled = anchoredDraggableState.targetValue == 0,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        onBack = internalOnDismissRequest,
                    )
                    .widthIn(max = 460.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    )
                    .then(
                        if (enableSwipeDismiss) {
                            Modifier.nestedScroll(
                                remember(anchoredDraggableState) {
                                    anchoredDraggableState.preUpPostDownNestedScrollConnection {
                                        scope.launch { anchoredDraggableState.settle(sheetAnimationSpec) }
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(modifier)
                    .offset {
                        IntOffset(
                            0,
                            anchoredDraggableState.offset
                                .takeIf { it.isFinite() }
                                ?.roundToInt()
                                ?: 0,
                        )
                    }
                    .anchoredDraggable(
                        state = anchoredDraggableState,
                        orientation = Orientation.Vertical,
                        enabled = enableSwipeDismiss,
                        flingBehavior = flingBehavior,
                    )
                    .navigationBarsPadding()
                    .statusBarsPadding(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    content()
                },
            )

            LaunchedEffect(anchoredDraggableState) {
                scope.launch { anchoredDraggableState.animateTo(0) }
                snapshotFlow { anchoredDraggableState.settledValue }
                    .drop(1)
                    .filter { it == 1 }
                    .collectLatest {
                        onDismissRequest()
                    }
            }
        }
    }
}

private fun <T> AnchoredDraggableState<T>.preUpPostDownNestedScrollConnection(
    onFling: (velocity: Float) -> Unit,
) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.toFloat()
        return if (delta < 0 && source == NestedScrollSource.UserInput) {
            dispatchRawDelta(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            dispatchRawDelta(available.toFloat()).toOffset()
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.toFloat()
        return if (toFling < 0 && offset > anchors.minPosition()) {
            onFling(toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        onFling(available.toFloat())
        return if (targetValue != settledValue) {
            available
        } else {
            Velocity.Zero
        }
    }

    private fun Float.toOffset(): Offset = Offset(0f, this)

    @JvmName("velocityToFloat")
    private fun Velocity.toFloat() = this.y

    @JvmName("offsetToFloat")
    private fun Offset.toFloat(): Float = this.y
}

private fun Modifier.predictiveBackAnimation(
    enabled: Boolean,
    transformOrigin: TransformOrigin,
    onBack: () -> Unit,
) = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { backEvent ->
                scale = lerp(1f, 0.85f, PredictiveBack.transform(backEvent.progress))
            }
            // Completion
            onBack()
        } catch (e: CancellationException) {
            // Cancellation
        } finally {
            animate(
                initialValue = scale,
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            ) { value, _ ->
                scale = value
            }
        }
    }
    Modifier.graphicsLayer {
        this.scaleX = scale
        this.scaleY = scale
        this.transformOrigin = transformOrigin
    }
}

private val sheetAnimationSpec = tween<Float>(durationMillis = 350)
