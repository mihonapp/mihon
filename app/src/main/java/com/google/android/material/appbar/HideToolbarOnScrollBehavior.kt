package com.google.android.material.appbar

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.marginTop
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.findChild
import eu.kanade.tachiyomi.widget.ElevationAppBarLayout
import kotlin.math.roundToLong

/**
 * Hide toolbar on scroll behavior for [AppBarLayout].
 *
 * Inside this package to access some package-private methods.
 */
class HideToolbarOnScrollBehavior : AppBarLayout.Behavior() {

    @ViewCompat.NestedScrollType
    private var lastStartedType: Int = 0

    private var offsetAnimator: ValueAnimator? = null

    private var toolbarHeight: Int = 0

    override fun onStartNestedScroll(
        parent: CoordinatorLayout,
        child: AppBarLayout,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int,
        type: Int
    ): Boolean {
        lastStartedType = type
        offsetAnimator?.cancel()
        return super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type)
    }

    override fun onStopNestedScroll(
        parent: CoordinatorLayout,
        layout: AppBarLayout,
        target: View,
        type: Int
    ) {
        super.onStopNestedScroll(parent, layout, target, type)
        if (toolbarHeight == 0) {
            toolbarHeight = layout.findChild<Toolbar>()?.height ?: 0
        }
        if (lastStartedType == ViewCompat.TYPE_TOUCH || type == ViewCompat.TYPE_NON_TOUCH) {
            animateToolbarVisibility(
                parent,
                layout,
                getTopBottomOffsetForScrollingSibling(layout) > -toolbarHeight / 2
            )
        }
    }

    override fun onFlingFinished(parent: CoordinatorLayout, layout: AppBarLayout) {
        super.onFlingFinished(parent, layout)
        animateToolbarVisibility(
            parent,
            layout,
            getTopBottomOffsetForScrollingSibling(layout) > -toolbarHeight / 2
        )
    }

    private fun getTopBottomOffsetForScrollingSibling(abl: AppBarLayout): Int {
        return topBottomOffsetForScrollingSibling - abl.marginTop
    }

    private fun animateToolbarVisibility(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        isVisible: Boolean
    ) {
        val current = getTopBottomOffsetForScrollingSibling(child)
        val target = if (isVisible) 0 else -toolbarHeight
        if (current == target) return

        offsetAnimator?.cancel()
        offsetAnimator = ValueAnimator().apply {
            interpolator = DecelerateInterpolator()
            duration = (150 * child.context.animatorDurationScale).roundToLong()
            addUpdateListener {
                setHeaderTopBottomOffset(coordinatorLayout, child, it.animatedValue as Int)
            }
            doOnEnd {
                if ((child as? ElevationAppBarLayout)?.isTransparentWhenNotLifted == true) {
                    child.isLifted = !isVisible
                }
            }
            setIntValues(current, target)
            start()
        }
    }
}
