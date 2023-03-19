package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.provider.Settings
import android.view.ViewPropertyAnimator
import android.view.animation.Animation
import androidx.constraintlayout.motion.widget.MotionScene.Transition

/**
 * Gets the duration multiplier for general animations on the device
 * @see Settings.Global.ANIMATOR_DURATION_SCALE
 */
val Context.animatorDurationScale: Float
    get() = Settings.Global.getFloat(this.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** Scale the duration of this [Animation] by [Context.animatorDurationScale] */
fun Animation.applySystemAnimatorScale(context: Context) {
    this.duration = (this.duration * context.animatorDurationScale).toLong()
}

/** Scale the duration of this [Transition] by [Context.animatorDurationScale] */
fun Transition.applySystemAnimatorScale(context: Context) {
    // End layout of cover expanding animation tends to break when the transition is less than ~25ms
    this.duration = (this.duration * context.animatorDurationScale).toInt().coerceAtLeast(25)
}

/** Scale the duration of this [ViewPropertyAnimator] by [Context.animatorDurationScale] */
fun ViewPropertyAnimator.applySystemAnimatorScale(context: Context): ViewPropertyAnimator = apply {
    this.duration = (this.duration * context.animatorDurationScale).toLong()
}
