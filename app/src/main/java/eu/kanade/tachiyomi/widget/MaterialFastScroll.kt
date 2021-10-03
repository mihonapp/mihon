package eu.kanade.tachiyomi.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.fastscroller.FastScroller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPxEnd
import eu.kanade.tachiyomi.util.system.isLTR

class MaterialFastScroll @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FastScroller(context, attrs) {

    init {
        setViewsToUse(
            R.layout.material_fastscroll,
            R.id.fast_scroller_bubble,
            R.id.fast_scroller_handle,
        )
        autoHideEnabled = true
        ignoreTouchesOutsideHandle = true

        applyInsetter {
            type(navigationBars = true) {
                margin()
            }
        }
    }

    // Overridden to handle RTL
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (recyclerView.computeVerticalScrollRange() <= recyclerView.computeVerticalScrollExtent()) {
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // start: handle RTL differently
                if (
                    if (context.resources.isLTR) {
                        event.x < handle.x - ViewCompat.getPaddingStart(handle)
                    } else {
                        event.x > handle.width + ViewCompat.getPaddingStart(handle)
                    }
                ) return false
                // end

                if (ignoreTouchesOutsideHandle &&
                    (event.y < handle.y || event.y > handle.y + handle.height)
                ) {
                    return false
                }
                handle.isSelected = true
                notifyScrollStateChange(true)
                showBubble()
                showScrollbar()
                val y = event.y
                setBubbleAndHandlePosition(y)
                setRecyclerViewPosition(y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                setBubbleAndHandlePosition(y)
                setRecyclerViewPosition(y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handle.isSelected = false
                notifyScrollStateChange(false)
                hideBubble()
                if (autoHideEnabled) hideScrollbar()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun setBubbleAndHandlePosition(y: Float) {
        super.setBubbleAndHandlePosition(y)
        if (bubbleEnabled) {
            bubble.y = handle.y - bubble.height / 2f + handle.height / 2f
            bubble.translationX = (-45f).dpToPxEnd
        }
    }
}
