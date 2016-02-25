package eu.kanade.tachiyomi.ui.base.fab

import android.content.Context
import android.support.design.widget.FloatingActionButton
import android.support.v4.view.animation.FastOutSlowInInterpolator
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import eu.kanade.tachiyomi.R

class FABAnimationUpDown() : FABAnimationBase()
{
    override var mIsAnimatingOut: Boolean = false
        get() = super.mIsAnimatingOut

    private val INTERPOLATOR = FastOutSlowInInterpolator()

    /**
     * Needed to prevent NoSuchMethodException
     */
    constructor(ctx: Context, attrs: AttributeSet) : this() { }

    override fun animateOut(button: FloatingActionButton) {
        super.animateIn(button)
        val anim = AnimationUtils.loadAnimation(button.context, R.anim.fab_hide_to_bottom)
        anim.interpolator = INTERPOLATOR
        anim.duration = 200L
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                mIsAnimatingOut = true
            }

            override fun onAnimationEnd(animation: Animation) {
                mIsAnimatingOut = false
                button.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })
        button.startAnimation(anim)

    }

    override fun animateIn(button: FloatingActionButton) {
        super.animateOut(button)
        button.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(button.context, R.anim.fab_show_from_bottom)
        anim.duration = 200L
        anim.interpolator = INTERPOLATOR
        button.startAnimation(anim)
    }
}