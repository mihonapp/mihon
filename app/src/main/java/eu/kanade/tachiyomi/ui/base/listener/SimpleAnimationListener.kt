package eu.kanade.tachiyomi.ui.base.listener

import android.view.animation.Animation

open class SimpleAnimationListener : Animation.AnimationListener {
    override fun onAnimationRepeat(animation: Animation) {}

    override fun onAnimationEnd(animation: Animation) {}

    override fun onAnimationStart(animation: Animation) {}
}