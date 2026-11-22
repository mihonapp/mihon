package eu.kanade.tachiyomi.ui.reader.viewer.compose.pager

import android.view.VelocityTracker
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.viewer.compose.pageSettleSpec
import kotlin.math.abs
import kotlin.math.floor

/**
 * Releases a drag the way ViewPager did: a fling of 400dp/s over 25dp turns the page its way, otherwise the page
 * turns once dragged 60% across, and the pager settles at ViewPager's speed.
 */
class ViewPagerFlingBehavior(
    private val state: PagerState,
    density: Density,
    touchSlop: Float,
) : TargetedFlingBehavior {

    private val minimumVelocity = with(density) { 400.dp.toPx() }

    // ViewPager measured the finger's whole travel; a drag here starts past the touch slop
    private val flingDistance = with(density) { 25.dp.toPx() } - touchSlop

    /**
     * The page the pager rested on before this drag, ViewPager's current item. The pager's own settled page moves
     * on mid drag.
     */
    var restingPage = state.currentPage

    /** The finger's speed at the lift as ViewPager's tracker read it, along the pages: see [trackReleaseVelocity]. */
    var releaseVelocity = 0f

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val pageSize = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
        if (pageSize <= 0 || state.pageCount == 0) return initialVelocity

        val position = state.currentPage + state.currentPageOffsetFraction
        val leadingPage = floor(position).toInt()
        val offset = position - leadingPage
        val dragged = (position - restingPage) * pageSize
        // Compose's tracker reads a finger held still a moment before the lift as stopped; the platform's may not
        val velocity = releaseVelocity
            .takeIf { abs(initialVelocity) < minimumVelocity && abs(it) > abs(initialVelocity) && it * dragged > 0 }
            ?: initialVelocity

        val target = if (abs(dragged) > flingDistance && abs(velocity) > minimumVelocity) {
            if (velocity > 0) leadingPage + 1 else leadingPage
        } else {
            val truncator = if (leadingPage >= restingPage) 0.4f else 0.6f
            leadingPage + (offset + truncator).toInt()
        }
            .coerceIn(0, state.pageCount - 1)

        val distance = (target - position) * pageSize
        var scrolled = 0f
        animate(0f, distance, animationSpec = pageSettleSpec(distance, pageSize, velocity)) { value, _ ->
            scrolled += scrollBy(value - scrolled)
            onRemainingDistanceUpdated(distance - scrolled)
        }
        return 0f
    }
}

/**
 * Tracks the finger for [ViewPagerFlingBehavior.releaseVelocity] with the platform's tracker, which ViewPager
 * used, so a pause before the lift counts as it did there on each Android version. Watches without consuming.
 */
fun Modifier.trackReleaseVelocity(behavior: ViewPagerFlingBehavior, isHorizontal: Boolean) =
    pointerInput(behavior, isHorizontal) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker.obtain()
            var single = true
            behavior.releaseVelocity = 0f
            try {
                currentEvent.motionEvent?.let(tracker::addMovement)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) single = false
                    event.motionEvent?.let(tracker::addMovement)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (single) {
                            tracker.computeCurrentVelocity(1000)
                            behavior.releaseVelocity = -(if (isHorizontal) tracker.xVelocity else tracker.yVelocity)
                        }
                        break
                    }
                }
            } finally {
                tracker.recycle()
            }
        }
    }
