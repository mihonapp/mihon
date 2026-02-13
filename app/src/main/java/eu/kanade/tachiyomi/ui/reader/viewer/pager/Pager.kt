package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.viewpager.widget.DirectionalViewPager
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import kotlin.math.abs

/**
 * Pager implementation that listens for tap and long tap and allows temporarily disabling touch
 * events in order to work with child views that need to disable touch events on this parent. The
 * pager can also be declared to be vertical by creating it with [isHorizontal] to false.
 */
open class Pager(
    context: Context,
    private val isHorizontalPager: Boolean = true,
) : DirectionalViewPager(context, isHorizontalPager) {

    var tapListener: ((MotionEvent) -> Unit)? = null

    var longTapListener: ((MotionEvent) -> Boolean)? = null

    var animatePageSwipe = true

    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var instantSwipeDone = false

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

    private val gestureDetector = GestureDetectorWithLongTap(context, gestureListener)

    private var isGestureDetectorEnabled = true

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!animatePageSwipe) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownX = ev.rawX
                    swipeDownY = ev.rawY
                    instantSwipeDone = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!instantSwipeDone) {
                        val dx = ev.rawX - swipeDownX
                        val dy = ev.rawY - swipeDownY
                        val touchSlop = ViewConfiguration.get(context).scaledPagingTouchSlop
                        val swipeDetected = if (isHorizontalPager) {
                            abs(dx) > touchSlop && abs(dx) > abs(dy)
                        } else {
                            abs(dy) > touchSlop && abs(dy) > abs(dx)
                        }
                        if (swipeDetected) {
                            instantSwipeDone = true
                            val direction = if (isHorizontalPager) {
                                if (dx > 0) -1 else 1
                            } else {
                                if (dy > 0) -1 else 1
                            }
                            val newPos = currentItem + direction
                            val count = adapter?.count ?: 0
                            if (newPos in 0 until count) {
                                setCurrentItem(newPos, false)
                            }
                        }
                    }
                }
            }
            if (instantSwipeDone) {
                if (isGestureDetectorEnabled) {
                    gestureDetector.onTouchEvent(ev)
                }
                if (ev.actionMasked == MotionEvent.ACTION_UP ||
                    ev.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    instantSwipeDone = false
                }
                return true
            }
        }

        val handled = super.dispatchTouchEvent(ev)
        if (isGestureDetectorEnabled) {
            gestureDetector.onTouchEvent(ev)
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!animatePageSwipe) {
            return false
        }
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!animatePageSwipe) {
            return false
        }
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
}
