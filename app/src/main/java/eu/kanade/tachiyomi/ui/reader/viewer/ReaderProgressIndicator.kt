package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import androidx.annotation.IntRange
import androidx.dynamicanimation.animation.DynamicAnimation
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * A wrapper for [CircularProgressIndicator] that always rotates while being determinate.
 *
 * By always rotating we give the feedback to the user that the application isn't 'stuck',
 * and by making it determinate the user also approximately knows how much the operation will take.
 */
class ReaderProgressIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val indicator: CircularProgressIndicator

    private val rotateAnimation by lazy {
        RotateAnimation(
            0F,
            360F,
            Animation.RELATIVE_TO_SELF,
            0.5F,
            Animation.RELATIVE_TO_SELF,
            0.5F
        ).apply {
            interpolator = LinearInterpolator()
            repeatCount = Animation.INFINITE
            duration = 4000
        }
    }

    init {
        layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        indicator = CircularProgressIndicator(context)
        indicator.max = 100
        indicator.isIndeterminate = true
        addView(indicator)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRotateAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updateRotateAnimation()
    }

    fun show() {
        indicator.show()
        updateRotateAnimation()
    }

    fun hide() {
        indicator.hide()
        updateRotateAnimation()
    }

    /**
     * Sets the current indicator progress to the specified value.
     *
     * @param progress Indicator will be set indeterminate if this value is 0
     */
    fun setProgress(@IntRange(from = 0, to = 100) progress: Int, animated: Boolean = true) {
        if (progress > 0) {
            indicator.setProgressCompat(progress, animated)
        } else if (!indicator.isIndeterminate) {
            indicator.hide()
            indicator.isIndeterminate = true
            indicator.show()
        }
        updateRotateAnimation()
    }

    fun setCompleteProgressAndHide() {
        val listener = object : DynamicAnimation.OnAnimationEndListener {
            override fun onAnimationEnd(
                animation: DynamicAnimation<*>?,
                canceled: Boolean,
                value: Float,
                velocity: Float
            ) {
                hide()
                indicator.progressDrawable?.removeSpringAnimationEndListener(this)
            }
        }

        indicator.progressDrawable?.addSpringAnimationEndListener(listener)
        indicator.setProgressCompat(100, true)
        updateRotateAnimation(forceRotate = true)
    }

    private fun updateRotateAnimation(forceRotate: Boolean = false) {
        if (forceRotate || (indicator.isShown && !indicator.isIndeterminate)) {
            if (animation == null && isAttachedToWindow) {
                startAnimation(rotateAnimation)
            }
        } else {
            clearAnimation()
        }
    }
}
