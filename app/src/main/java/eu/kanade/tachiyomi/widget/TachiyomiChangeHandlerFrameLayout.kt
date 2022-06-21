package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.bluelinelabs.conductor.ChangeHandlerFrameLayout

/**
 * [ChangeHandlerFrameLayout] with the ability to draw behind the header sibling in [CoordinatorLayout].
 * The layout behavior of this view is set to [TachiyomiScrollingViewBehavior] and should not be changed.
 */
class TachiyomiChangeHandlerFrameLayout(
    context: Context,
    attrs: AttributeSet,
) : ChangeHandlerFrameLayout(context, attrs), CoordinatorLayout.AttachedBehavior {

    /**
     * If true, this view will draw behind the header sibling.
     *
     * @see TachiyomiScrollingViewBehavior.shouldHeaderOverlap
     */
    var overlapHeader = false
        set(value) {
            if (field != value) {
                field = value
                (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = behavior.apply {
                    shouldHeaderOverlap = value
                }
                if (!value) {
                    // The behavior doesn't reset translationY when shouldHeaderOverlap is false
                    translationY = 0F
                }
                forceLayout()
            }
        }

    fun enableScrollingBehavior(enable: Boolean) {
        (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = if (enable) {
            behavior.apply {
                shouldHeaderOverlap = overlapHeader
            }
        } else null
        if (!enable) {
            // The behavior doesn't reset translationY when shouldHeaderOverlap is false
            translationY = 0F
        }
        forceLayout()
    }

    override fun getBehavior() = TachiyomiScrollingViewBehavior()
}
