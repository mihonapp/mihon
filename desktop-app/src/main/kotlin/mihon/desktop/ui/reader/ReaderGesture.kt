package mihon.desktop.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import mihon.desktop.reader.input.InputPoint
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport

/**
 * Pointer-input edge for the reader surface. It only translates Compose pointer events into
 * semantic callbacks; state clamping and double-tap decisions live in [ReaderGesturePolicy].
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun ReaderGestureArea(
    enabled: Boolean,
    onPress: (normalizedX: Float) -> Unit,
    onTap: (normalizedX: Float) -> Unit,
    onDoubleTap: (viewport: ReaderViewport) -> Unit,
    onPan: (delta: ReaderPan, viewport: ReaderViewport) -> Unit,
    onZoomBy: (factor: Float, centroid: InputPoint, viewport: ReaderViewport) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (normalizedX: Float) -> Unit = {},
    onSecondaryClick: () -> Unit = {},
    onPointerMove: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnSecondaryClick by rememberUpdatedState(onSecondaryClick)
    val currentOnPointerMove by rememberUpdatedState(onPointerMove)
    val currentOnPan by rememberUpdatedState(onPan)
    val currentOnZoomBy by rememberUpdatedState(onZoomBy)
    val secondaryClickModifier = if (enabled) {
        Modifier.onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
            if (event.button == PointerButton.Secondary) {
                currentOnSecondaryClick()
                event.changes.forEach { it.consume() }
            }
        }
    } else {
        Modifier
    }
    val pointerMoveModifier = if (enabled) {
        Modifier.onPointerEvent(PointerEventType.Move, PointerEventPass.Initial) {
            currentOnPointerMove()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val areaSize = size
                detectTapGestures(
                    onPress = { position ->
                        val width = areaSize.width.coerceAtLeast(1)
                        currentOnPress((position.x / width).coerceIn(0f, 1f))
                    },
                    onTap = { position ->
                        val width = areaSize.width.coerceAtLeast(1)
                        currentOnTap((position.x / width).coerceIn(0f, 1f))
                    },
                    onDoubleTap = {
                        currentOnDoubleTap(areaSize.toReaderViewport())
                    },
                    onLongPress = { position ->
                        val width = areaSize.width.coerceAtLeast(1)
                        currentOnLongPress((position.x / width).coerceIn(0f, 1f))
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val areaSize = size
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val viewport = areaSize.toReaderViewport()
                    if (zoom != 1f) {
                        val inputCentroid = InputPoint(
                            x = (centroid.x / viewport.width).coerceIn(0f, 1f),
                            y = (centroid.y / viewport.height).coerceIn(0f, 1f),
                        )
                        currentOnZoomBy(zoom, inputCentroid, viewport)
                    } else if (pan != Offset.Zero) {
                        currentOnPan(ReaderPan(pan.x, pan.y), viewport)
                    }
                }
            }
            .then(secondaryClickModifier)
            .then(pointerMoveModifier),
        content = content,
    )
}

private fun IntSize.toReaderViewport(): ReaderViewport = ReaderViewport(
    width = width.coerceAtLeast(1),
    height = height.coerceAtLeast(1),
)
