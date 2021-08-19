package eu.kanade.tachiyomi.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.animation.AnimationUtils
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import eu.kanade.tachiyomi.R

class ElevationAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppBarLayout(context, attrs) {

    private var lifted = true
    private var transparent = false

    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }

    private var elevationAnimator: ValueAnimator? = null
    private var backgroundAlphaAnimator: ValueAnimator? = null

    var isTransparentWhenNotLifted = false
        set(value) {
            if (field != value) {
                field = value
                updateBackgroundAlpha()
            }
        }

    /**
     * Disabled. Lift on scroll is handled manually with [TachiyomiCoordinatorLayout]
     */
    override fun isLiftOnScroll(): Boolean = false

    override fun isLifted(): Boolean = lifted

    override fun setLifted(lifted: Boolean): Boolean {
        return if (this.lifted != lifted) {
            this.lifted = lifted
            val from = elevation
            val to = if (lifted) {
                resources.getDimension(R.dimen.design_appbar_elevation)
            } else {
                0F
            }

            elevationAnimator?.cancel()
            elevationAnimator = ValueAnimator.ofFloat(from, to).apply {
                duration = resources.getInteger(R.integer.app_bar_elevation_anim_duration).toLong()
                interpolator = AnimationUtils.LINEAR_INTERPOLATOR
                addUpdateListener {
                    elevation = it.animatedValue as Float
                }
                start()
            }

            updateBackgroundAlpha()
            true
        } else {
            false
        }
    }

    private fun updateBackgroundAlpha() {
        val newTransparent = if (lifted) false else isTransparentWhenNotLifted
        if (transparent != newTransparent) {
            transparent = newTransparent
            val fromAlpha = if (transparent) 255 else 0
            val toAlpha = if (transparent) 0 else 255

            backgroundAlphaAnimator?.cancel()
            backgroundAlphaAnimator = ValueAnimator.ofInt(fromAlpha, toAlpha).apply {
                duration = resources.getInteger(R.integer.app_bar_elevation_anim_duration).toLong()
                interpolator = AnimationUtils.LINEAR_INTERPOLATOR
                addUpdateListener {
                    val alpha = it.animatedValue as Int
                    background.alpha = alpha
                    toolbar?.background?.alpha = alpha
                    statusBarForeground?.alpha = alpha
                }
                start()
            }
        }
    }
}
