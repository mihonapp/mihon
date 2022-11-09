package eu.kanade.tachiyomi.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.ViewPropertyAnimator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.applySystemAnimatorScale
import eu.kanade.tachiyomi.util.system.pxToDp
import kotlin.math.max

class TachiyomiBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.bottomNavigationStyle,
    defStyleRes: Int = R.style.Widget_Design_BottomNavigationView,
) : BottomNavigationView(context, attrs, defStyleAttr, defStyleRes) {

    private var currentAnimator: ViewPropertyAnimator? = null

    private var currentState = STATE_UP

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also {
            it.currentState = currentState
            it.translationY = translationY
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            super.setTranslationY(state.translationY)
            currentState = state.currentState
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun setTranslationY(translationY: Float) {
        // Disallow translation change when state down
        if (currentState == STATE_DOWN) return
        super.setTranslationY(translationY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bottomNavPadding = h.pxToDp.dp
    }

    /**
     * Shows this view up.
     */
    fun slideUp() = post {
        currentAnimator?.cancel()
        clearAnimation()

        currentState = STATE_UP
        animateTranslation(
            0F,
            SLIDE_UP_ANIMATION_DURATION,
            LinearOutSlowInInterpolator(),
        )
        bottomNavPadding = height.pxToDp.dp
    }

    /**
     * Hides this view down. [setTranslationY] won't work until [slideUp] is called.
     */
    fun slideDown() = post {
        currentAnimator?.cancel()
        clearAnimation()

        currentState = STATE_DOWN
        animateTranslation(
            height.toFloat(),
            SLIDE_DOWN_ANIMATION_DURATION,
            FastOutLinearInInterpolator(),
        )
        bottomNavPadding = 0.dp
    }

    private fun animateTranslation(targetY: Float, duration: Long, interpolator: TimeInterpolator) {
        currentAnimator = animate()
            .translationY(targetY)
            .setInterpolator(interpolator)
            .setDuration(duration)
            .applySystemAnimatorScale(context)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        currentAnimator = null
                        postInvalidate()
                    }
                },
            )
    }

    internal class SavedState : AbsSavedState {
        var currentState = STATE_UP
        var translationY = 0F

        constructor(superState: Parcelable) : super(superState)

        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            currentState = source.readInt()
            translationY = source.readFloat()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(currentState)
            out.writeFloat(translationY)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.ClassLoaderCreator<SavedState> = object : Parcelable.ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel, loader: ClassLoader): SavedState {
                    return SavedState(source, loader)
                }

                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source, null)
                }

                override fun newArray(size: Int): Array<SavedState> {
                    return newArray(size)
                }
            }
        }
    }

    companion object {
        private const val STATE_DOWN = 1
        private const val STATE_UP = 2

        private const val SLIDE_UP_ANIMATION_DURATION = 225L
        private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

        private var bottomNavPadding by mutableStateOf(0.dp)

        /**
         * Merges [bottomNavPadding] to the origin's [PaddingValues] bottom side.
         */
        @ReadOnlyComposable
        @Composable
        fun withBottomNavPadding(origin: PaddingValues = PaddingValues()): PaddingValues {
            val layoutDirection = LocalLayoutDirection.current
            return PaddingValues(
                start = origin.calculateStartPadding(layoutDirection),
                top = origin.calculateTopPadding(),
                end = origin.calculateEndPadding(layoutDirection),
                bottom = max(origin.calculateBottomPadding(), bottomNavPadding),
            )
        }

        /**
         * @see withBottomNavPadding
         */
        @ReadOnlyComposable
        @Composable
        fun withBottomNavInset(origin: WindowInsets): WindowInsets {
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            return WindowInsets(
                left = origin.getLeft(density, layoutDirection),
                top = origin.getTop(density),
                right = origin.getRight(density, layoutDirection),
                bottom = max(origin.getBottom(density), with(density) { bottomNavPadding.roundToPx() }),
            )
        }
    }
}
