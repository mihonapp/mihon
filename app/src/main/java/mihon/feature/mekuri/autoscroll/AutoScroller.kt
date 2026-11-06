package mihon.feature.mekuri.autoscroll

import android.view.Choreographer
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mekuri: smooth hands-free scrolling for the long strip reader.
 *
 * Scrolls [recycler] a little every display frame at a speed picked from [SPEED_RANGE]. While a finger is on the
 * screen it holds still (so you can drag back or tap for the menu), and carries on when the finger lifts. It stops
 * by itself at the very end of what is loaded.
 */
class AutoScroller(
    private val recycler: RecyclerView,
    private val window: Window,
) : Choreographer.FrameCallback {

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _speed = MutableStateFlow(DEFAULT_SPEED)
    val speedState: StateFlow<Int> = _speed.asStateFlow()

    /** A step in [SPEEDS] (1-based): 1 is 0.25×, 4 is 1×, the last is 10×. */
    var speed: Int
        get() = _speed.value
        set(value) {
            _speed.value = value.coerceIn(SPEED_RANGE)
        }

    /** Rough seconds to scroll through [pagesLeft] more pages at the current speed, from the pages on screen. */
    fun estimateSecondsLeft(pagesLeft: Int): Int? {
        if (pagesLeft <= 0) return 0
        val heights = (0 until recycler.childCount).map { recycler.getChildAt(it).height }.filter { it > 0 }
        if (heights.isEmpty()) return null
        val remainingPx = heights.average() * pagesLeft
        return (remainingPx / pixelsPerSecond()).toInt()
    }

    private val density = recycler.resources.displayMetrics.density
    private var held = false
    private var lastFrameNanos = 0L
    private var remainder = 0f
    private var addedKeepScreenOn = false

    fun toggle() {
        if (_playing.value) stop() else start()
    }

    fun start() {
        if (_playing.value) return
        _playing.value = true
        lastFrameNanos = 0L
        startedAtNanos = 0L
        remainder = 0f
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            addedKeepScreenOn = true
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!_playing.value) return
        _playing.value = false
        held = false
        Choreographer.getInstance().removeFrameCallback(this)
        if (addedKeepScreenOn) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            addedKeepScreenOn = false
        }
    }

    /** Feed every touch on the reader here: a finger down holds the scroll, lifting it lets it carry on. */
    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> held = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                held = false
                lastFrameNanos = 0L
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!_playing.value) return
        if (!held && lastFrameNanos != 0L) {
            val seconds = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            val px = pixelsPerSecond() * seconds + remainder
            val step = px.toInt()
            remainder = px - step
            if (step > 0) recycler.scrollBy(0, step)
            if (!recycler.canScrollVertically(1)) {
                stop()
                return
            }
        }
        lastFrameNanos = frameTimeNanos
        if (startedAtNanos == 0L) startedAtNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    private var startedAtNanos = 0L

    private fun pixelsPerSecond(): Float {
        // Ease in over the first ~0.4 s so starting doesn't jolt the page
        val ramp = if (startedAtNanos ==
            0L
        ) {
            1f
        } else {
            ((lastFrameNanos - startedAtNanos) / 400_000_000f).coerceIn(0.15f, 1f)
        }
        return ONE_X_DP_PER_SECOND * multiplier(speed) * density * ramp
    }

    companion object {
        /** Speed steps, as multiples of a comfortable reading pace. */
        private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f, 8f, 10f)
        val SPEED_RANGE = 1..SPEEDS.size
        const val DEFAULT_SPEED = 4
        private const val ONE_X_DP_PER_SECOND = 92f

        fun multiplier(speed: Int): Float = SPEEDS[speed.coerceIn(SPEED_RANGE) - 1]

        /** Speed as a multiplier label, like "1.25×" or "10×". */
        fun label(speed: Int): String {
            val x = multiplier(speed)
            return if (x == x.toInt().toFloat()) "${x.toInt()}×" else "$x×"
        }
    }
}
