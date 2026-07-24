package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.viewpager.widget.DirectionalViewPager
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.lang.reflect.Field

/**
 * Pager implementation that listens for tap and long tap and allows temporarily disabling touch
 * events in order to work with child views that need to disable touch events on this parent. The
 * pager can also be declared to be vertical by creating it with [isHorizontal] to false.
 */
open class Pager(
    context: Context,
    isHorizontal: Boolean = true,
) : DirectionalViewPager(context, isHorizontal) {

    /**
     * Tap listener function to execute when a tap is detected.
     */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /**
     * Long tap listener function to execute when a long tap is detected.
     */
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    /**
     * Gesture listener that implements tap and long tap events.
     */
    private val gestureListener = object : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /**
     * Gesture detector which handles motion events.
     */
    private val gestureDetector = GestureDetectorWithLongTap(context, gestureListener)

    /**
     * Whether the gesture detector is currently enabled.
     */
    private var isGestureDetectorEnabled = true

    /**
     * Dispatches a touch event.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (isGestureDetectorEnabled) {
            gestureDetector.onTouchEvent(ev)
        }
        return handled
    }

    /**
     * Whether the given [ev] should be intercepted. Only used to prevent crashes when child
     * views manipulate [requestDisallowInterceptTouchEvent].
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Handles a touch event. Only used to prevent crashes when child views manipulate
     * [requestDisallowInterceptTouchEvent].
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onTouchEvent(ev)
        } catch (e: NullPointerException) {
            false
        } catch (e: IndexOutOfBoundsException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Executes the given key event when this pager has focus. Just do nothing because the reader
     * already dispatches key events to the viewer and has more control than this method.
     */
    override fun executeKeyEvent(event: KeyEvent): Boolean {
        // Disable viewpager's default key event handling
        return false
    }

    /**
     * Enables or disables the gesture detector.
     */
    fun setGestureDetectorEnabled(enabled: Boolean) {
        isGestureDetectorEnabled = enabled
    }

    /**
     * The ViewPager's private [Scroller] field, looked up once across the class hierarchy so the
     * page-transition animation can be swapped at runtime.
     */
    private val scrollerField: Field? by lazy {
        generateSequence<Class<*>>(javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField("mScroller") }.getOrNull() }
            .firstOrNull()
            ?.apply { isAccessible = true }
    }

    /**
     * The original scroller, cached so a `null` interpolator restores native behavior.
     */
    private var originalScroller: Scroller? = null

    /**
     * Swaps the scroller used to settle page transitions. A `null` [interpolator] restores the
     * ViewPager's native scroller; otherwise the chosen curve runs over a fixed [durationMs]. Falls
     * back silently to the default behavior if the internal scroller can't be accessed.
     */
    fun setTransitionAnimation(interpolator: Interpolator?, durationMs: Int) {
        val field = scrollerField ?: return
        try {
            if (originalScroller == null) {
                originalScroller = field.get(this) as? Scroller
            }
            val scroller = if (interpolator == null) {
                originalScroller
            } else {
                ReaderPagerScroller(context, interpolator, durationMs)
            }
            field.set(this, scroller)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to apply reader page transition animation" }
        }
    }
}
