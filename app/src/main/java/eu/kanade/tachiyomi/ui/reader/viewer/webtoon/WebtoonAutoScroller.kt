package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.Choreographer
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scrolls a long strip at a constant speed, one display frame at a time, so it stays smooth on high refresh rate
 * screens.
 *
 * Scrolling holds still while [shouldHold] returns true (the menu is open, for example), while a finger is on the
 * list and while the list is flinging or settling on its own, and resumes afterwards. It stops at the end of the
 * strip.
 *
 * @param speedProvider the speed in dp per second, read every frame so changes apply immediately.
 */
class WebtoonAutoScroller(
    private val recycler: WebtoonRecyclerView,
    private val speedProvider: () -> Float,
    private val shouldHold: () -> Boolean,
) : Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()

    private val mutableIsRunning = MutableStateFlow(false)

    /**
     * Whether auto-scroll is on. It stays on while scrolling is held.
     */
    val isRunning: StateFlow<Boolean> = mutableIsRunning.asStateFlow()

    private var isTouching = false
    private var lastFrameTimeNanos = 0L

    /**
     * Fraction of a pixel left over from previous frames, so slow speeds still move.
     */
    private var pendingPixels = 0f

    init {
        recycler.touchStateListener = { touching -> isTouching = touching }
    }

    fun start() {
        if (mutableIsRunning.value) return
        if (!recycler.canScrollVertically(1)) return
        mutableIsRunning.value = true
        lastFrameTimeNanos = 0L
        pendingPixels = 0f
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!mutableIsRunning.value) return
        mutableIsRunning.value = false
        choreographer.removeFrameCallback(this)
    }

    fun toggle() {
        if (mutableIsRunning.value) stop() else start()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!mutableIsRunning.value) return

        val elapsedSeconds = if (lastFrameTimeNanos == 0L) {
            0f
        } else {
            // Cap the step so a dropped frame or a stall doesn't make the strip jump
            ((frameTimeNanos - lastFrameTimeNanos) / NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_STEP_SECONDS)
        }
        lastFrameTimeNanos = frameTimeNanos

        val isHeld = isTouching || recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE || shouldHold()
        if (isHeld) {
            pendingPixels = 0f
        } else if (elapsedSeconds > 0f) {
            if (!recycler.canScrollVertically(1)) {
                stop()
                return
            }
            val density = recycler.resources.displayMetrics.density
            pendingPixels += speedProvider() * density * elapsedSeconds
            val pixels = pendingPixels.toInt()
            if (pixels > 0) {
                pendingPixels -= pixels
                recycler.scrollBy(0, pixels)
            }
        }

        choreographer.postFrameCallback(this)
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_STEP_SECONDS = 0.05f
