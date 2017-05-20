package eu.kanade.tachiyomi.ui.main

import android.support.design.widget.TabLayout
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.visible

class TabsAnimator(val tabs: TabLayout) {

    /**
     * The default height of the tab layout. It's unknown until the view is layout.
     */
    private var tabsHeight = 0

    /**
     * Whether the last state of the tab layout is [View.VISIBLE] or [View.GONE].
     */
    private var isLastStateShown = true

    /**
     * Interpolator used to animate the tab layout.
     */
    private val interpolator = DecelerateInterpolator()

    /**
     * Duration of the animation.
     */
    private val duration = 300L

    /**
     * Animation used to expand the tab layout.
     */
    private val expandAnimation = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            setHeight((tabsHeight * interpolatedTime).toInt())
            tabs.requestLayout()
        }

        override fun willChangeBounds(): Boolean {
            return true
        }
    }

    /**
     * Animation used to collapse the tab layout.
     */
    private val collapseAnimation = object : Animation() {

        /**
         * Property holding the height of the tabs at the moment the animation is started. Useful
         * to provide a seamless animation.
         */
        private var startHeight = 0

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            if (interpolatedTime == 0f) {
                startHeight = tabs.height
            } else if (interpolatedTime == 1f) {
                tabs.gone()
            } else {
                setHeight((startHeight * (1 - interpolatedTime)).toInt())
                tabs.requestLayout()
            }
        }

        override fun willChangeBounds(): Boolean {
            return true
        }
    }

    init {
        collapseAnimation.duration = duration
        collapseAnimation.interpolator = interpolator
        expandAnimation.duration = duration
        expandAnimation.interpolator = interpolator

        isLastStateShown = tabs.visibility == View.VISIBLE
        tabs.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (tabs.height > 0) {
                        tabs.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        // Save the tabs default height.
                        tabsHeight = tabs.height

                        // Now that we know the height, set the initial height and visibility.
                        if (isLastStateShown) {
                            setHeight(tabsHeight)
                            tabs.visible()
                        } else {
                            setHeight(0)
                            tabs.gone()
                        }
                    }
                }
            }
        )
    }

    /**
     * Sets the height of the tab layout.
     *
     * @param newHeight The new height of the tab layout.
     */
    private fun setHeight(newHeight: Int) {
        tabs.layoutParams.height = newHeight
    }

    /**
     * Expands the tab layout with an animation.
     */
    fun expand() {
        cancelCurrentAnimations()
        tabs.visible()
        if (isMeasured && (!isLastStateShown || tabs.height != tabsHeight)) {
            tabs.startAnimation(expandAnimation)
        }
        isLastStateShown = true
    }

    /**
     * Collapse the tab layout with an animation.
     */
    fun collapse() {
        cancelCurrentAnimations()
        if (isMeasured && (isLastStateShown || tabs.height != 0)) {
            tabs.startAnimation(collapseAnimation)
        }
        isLastStateShown = false
    }

    /**
     * Cancels all the currently running animations.
     */
    private fun cancelCurrentAnimations() {
        collapseAnimation.cancel()
        expandAnimation.cancel()
    }

    /**
     * Returns whether the tab layout has a known height.
     */
    val isMeasured: Boolean
        get() = tabsHeight > 0

}