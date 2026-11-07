package eu.kanade.tachiyomi.ui.reader.viewer.compose.webtoon

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Zoom of the webtoon viewer: pinch and quick scale, double tap zoom, and panning the zoomed list, which is drawn
 * scaled by [scale] and moved by [translationX] and [translationY].
 *
 * @param canPanVertically whether the list is at its start or end, the only places a zoomed list pans vertically.
 */
class WebtoonZoom(
    private val canPanVertically: () -> Boolean,
) {

    /**
     * Where zoom and fling animations run: the viewer's composition, whose frame clock they step with.
     */
    var animationScope: CoroutineScope? = null

    var scale by mutableFloatStateOf(DEFAULT_RATE)
        private set
    var translationX by mutableFloatStateOf(0f)
        private set
    var translationY by mutableFloatStateOf(0f)
        private set

    /**
     * Size of the viewer, which the list fills while not zoomed out.
     */
    var viewportWidth = 0
    var viewportHeight = 0

    /**
     * Height of the list, taller than the viewer while zoomed out so it still fills it once scaled.
     */
    val listHeight: Int
        get() = if (scale < 1) (viewportHeight / scale).toInt() else viewportHeight

    private val halfWidth get() = viewportWidth / 2
    private val halfHeight get() = listHeight / 2

    private var animation: Job? = null

    private val isZooming get() = animation?.isActive == true

    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && scale < DEFAULT_RATE) {
                zoom(DEFAULT_RATE, 0f, 0f)
            }
        }
    private val minRate
        get() = if (zoomOutDisabled) DEFAULT_RATE else MIN_RATE

    /**
     * Space each side of the pages, which a zoomed list can't pan past.
     */
    var sidePaddingPixels = 0
        set(value) {
            field = value
            translationX = getPositionX(translationX)
        }

    /**
     * Whether a double tap zooms, and a double tap and drag scales.
     */
    var doubleTapZoom = true

    private fun getPositionX(positionX: Float): Float {
        if (scale < 1) {
            return 0f
        }
        val maxPositionX = ((halfWidth - sidePaddingPixels) * scale - halfWidth).coerceAtLeast(0f)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (scale < 1) {
            return 0f
        }
        val maxPositionY = halfHeight * (scale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    /**
     * Animates to [toRate], moved by [toX] and [toY].
     */
    private fun zoom(toRate: Float, toX: Float, toY: Float) {
        val fromRate = scale
        val fromX = translationX
        val fromY = translationY
        animate(tween(ANIMATOR_DURATION_TIME, easing = EaseOutQuad)) { t ->
            setScaleRate(fromRate + (toRate - fromRate) * t)
            translationX = getPositionX(fromX + (toX - fromX) * t)
            translationY = fromY + (toY - fromY) * t
        }
    }

    private fun zoomFling(velocity: Offset) {
        if (scale <= 1f) return

        val fromX = translationX
        val fromY = translationY
        val toX = getPositionX(fromX + FLING_DISTANCE_FACTOR * velocity.x / 2)
        val panVertically = canPanVertically()
        val toY = if (panVertically) getPositionY(fromY + FLING_DISTANCE_FACTOR * velocity.y / 2) else fromY
        animate(tween(FLING_DURATION, easing = EaseOutQuad)) { t ->
            translationX = getPositionX(fromX + (toX - fromX) * t)
            if (panVertically) translationY = getPositionY(fromY + (toY - fromY) * t)
        }
    }

    private fun animate(spec: AnimationSpec<Float>, onFrame: (Float) -> Unit) {
        animation?.cancel()
        val scope = animationScope ?: return onFrame(1f)
        animation = scope.launch {
            animate(0f, 1f, animationSpec = spec) { value, _ -> onFrame(value) }
        }
    }

    private fun zoomScrollBy(dx: Float, dy: Float) {
        if (dx != 0f) {
            translationX = getPositionX(translationX + dx)
        }
        if (dy != 0f) {
            translationY = getPositionY(translationY + dy)
        }
    }

    private fun setScaleRate(rate: Float) {
        scale = rate
        translationX = getPositionX(translationX)
    }

    private fun onScale(scaleFactor: Float) {
        setScaleRate((scale * scaleFactor).coerceIn(minRate, MAX_SCALE_RATE))

        if (scale != DEFAULT_RATE) {
            translationX = getPositionX(translationX)
            translationY = getPositionY(translationY)
        } else {
            translationX = 0f
            translationY = 0f
        }
    }

    /**
     * Scales by [scaleFactor] about [focus], then moves by [pan], so the content under the fingers follows them.
     */
    private fun onPinch(scaleFactor: Float, focus: Offset, pan: Offset) {
        val fromScale = scale
        onScale(scaleFactor)
        if (scale == DEFAULT_RATE) return

        // The list is scaled about the middle of the viewer
        val focusX = focus.x - halfWidth
        val focusY = focus.y - viewportHeight / 2f
        val change = scale / fromScale
        translationX = getPositionX((translationX - focusX) * change + focusX + pan.x)
        translationY = getPositionY((translationY - focusY) * change + focusY + pan.y)
    }

    private fun onScaleEnd() {
        if (scale < minRate) {
            zoom(minRate, 0f, 0f)
        }
    }

    private fun onDoubleTap(position: Offset) {
        if (isZooming || !doubleTapZoom) return
        if (scale != DEFAULT_RATE) {
            zoom(DEFAULT_RATE, 0f, 0f)
        } else {
            val toScale = 2f
            zoom(toScale, (halfWidth - position.x) * (toScale - 1), (halfHeight - position.y) * (toScale - 1))
        }
    }

    private var lastUpTime = 0L
    private var lastUpPosition = Offset.Zero

    /**
     * Pinches, double taps with their quick scale, and drags and flings of the zoomed list. Seen after the pages
     * have had the events, consumed or not, so the list's scrolling doesn't hide them.
     */
    suspend fun PointerInputScope.detectZoomGestures(isEnabled: () -> Boolean) {
        val doubleTapSlop = 100.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!isEnabled()) return@awaitEachGesture

            val isSecondTap = doubleTapZoom &&
                down.uptimeMillis - lastUpTime < viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastUpPosition).getDistance() < doubleTapSlop
            val velocityTracker = VelocityTracker().apply { addPointerInputChange(down) }
            var pointerId = down.id
            var last = down.position
            var isDragging = false
            var isPinching = false
            var isQuickScaling = false

            while (true) {
                // Before the list's scrollable, which a pinch keeps from scrolling
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pressed = event.changes.filter { it.pressed }

                if (pressed.size > 1) {
                    isPinching = true
                    onPinch(event.calculateZoom(), event.calculateCentroid(useCurrent = false), event.calculatePan())
                    event.changes.forEach { it.consume() }
                    continue
                }
                if (isPinching) {
                    onScaleEnd()
                    isPinching = false
                    // The finger left down drags on from where it is
                    pressed.firstOrNull()?.let {
                        pointerId = it.id
                        last = it.position
                    }
                }

                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                velocityTracker.addPointerInputChange(change)
                val position = change.position
                if (!change.pressed) {
                    lastUpTime = change.uptimeMillis
                    lastUpPosition = position
                    when {
                        isQuickScaling -> onScaleEnd()
                        isSecondTap -> {
                            onDoubleTap(position)
                            // A third tap starts afresh rather than counting as another double tap
                            lastUpTime = 0L
                        }
                        isDragging -> zoomFling(velocityTracker.calculateVelocity().let { Offset(it.x, it.y) })
                    }
                    break
                }

                if (isSecondTap) {
                    if (!isQuickScaling && abs(position.y - down.position.y) > viewConfiguration.touchSlop) {
                        isQuickScaling = true
                        last = position
                    }
                    if (isQuickScaling) {
                        // Dragging down zooms in, as ScaleGestureDetector's quick scale did
                        onScale(1 + (position.y - last.y) / (viewportHeight / 2f).coerceAtLeast(1f))
                        last = position
                    }
                    continue
                }

                val panVertically = canPanVertically()
                if (!isDragging && scale > 1f) {
                    val moved = position - down.position
                    if (abs(moved.x) > viewConfiguration.touchSlop ||
                        (panVertically && abs(moved.y) > viewConfiguration.touchSlop)
                    ) {
                        isDragging = true
                        animation?.cancel()
                        last = position
                    }
                }
                if (isDragging) {
                    zoomScrollBy(position.x - last.x, if (panVertically) position.y - last.y else 0f)
                    last = position
                }
            }
        }
    }
}

private const val ANIMATOR_DURATION_TIME = 200
private const val FLING_DURATION = 400
private const val FLING_DISTANCE_FACTOR = 0.4f
private const val MIN_RATE = 0.5f
private const val DEFAULT_RATE = 1f
private const val MAX_SCALE_RATE = 3f
