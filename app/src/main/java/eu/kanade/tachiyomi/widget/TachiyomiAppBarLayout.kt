@file:Suppress("PackageDirectoryMismatch")

package com.google.android.material.appbar

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.FloatRange
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.material.animation.AnimationUtils
import com.google.android.material.shape.MaterialShapeDrawable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.findChild
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.HierarchyChangeEvent
import reactivecircus.flowbinding.android.view.hierarchyChangeEvents

/**
 * [AppBarLayout] with our own lift state handler and custom title alpha.
 *
 * Inside this package to access some package-private methods.
 */
class TachiyomiAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppBarLayout(context, attrs) {

    private var lifted = true

    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }

    @FloatRange(from = 0.0, to = 1.0)
    var titleTextAlpha = 1F
        set(value) {
            field = value
            titleTextView?.alpha = field
        }

    private var titleTextView: TextView? = null
        set(value) {
            field = value
            field?.alpha = titleTextAlpha
        }

    private var animatorSet: AnimatorSet? = null

    private var statusBarForegroundAnimator: ValueAnimator? = null
    private val offsetListener = OnOffsetChangedListener { appBarLayout, verticalOffset ->
        // Show status bar foreground when offset
        val foreground = (appBarLayout?.statusBarForeground as? MaterialShapeDrawable) ?: return@OnOffsetChangedListener
        val start = foreground.alpha
        val end = if (verticalOffset != 0) 255 else 0

        statusBarForegroundAnimator?.cancel()
        if (animatorSet?.isRunning == true) {
            foreground.alpha = end
            return@OnOffsetChangedListener
        }
        if (start != end) {
            statusBarForegroundAnimator = ValueAnimator.ofInt(start, end).apply {
                duration = resources.getInteger(R.integer.app_bar_elevation_anim_duration).toLong()
                interpolator = AnimationUtils.LINEAR_INTERPOLATOR
                addUpdateListener {
                    foreground.alpha = it.animatedValue as Int
                }
                start()
            }
        }
    }

    var isTransparentWhenNotLifted = false
        set(value) {
            if (field != value) {
                field = value
                updateStates()
            }
        }

    /**
     * Disabled. Lift on scroll is handled manually with [eu.kanade.tachiyomi.widget.TachiyomiCoordinatorLayout]
     */
    override fun isLiftOnScroll(): Boolean = false

    override fun isLifted(): Boolean = lifted

    override fun setLifted(lifted: Boolean): Boolean {
        return if (this.lifted != lifted) {
            this.lifted = lifted
            updateStates()
            true
        } else {
            false
        }
    }

    override fun setLiftedState(lifted: Boolean, force: Boolean): Boolean = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addOnOffsetChangedListener(offsetListener)
        toolbar.background.alpha = 0 // Use app bar background

        titleTextView = toolbar.findChild<TextView>()
        findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.let { scope ->
            toolbar.hierarchyChangeEvents()
                .onEach {
                    when (it) {
                        is HierarchyChangeEvent.ChildAdded -> {
                            if (it.child is TextView) {
                                titleTextView = it.child as TextView
                            }
                        }
                        is HierarchyChangeEvent.ChildRemoved -> {
                            if (it.child == titleTextView) {
                                titleTextView = null
                            }
                        }
                    }
                }
                .launchIn(scope)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeOnOffsetChangedListener(offsetListener)
    }

    @SuppressLint("Recycle")
    private fun updateStates() {
        val animators = mutableListOf<ValueAnimator>()

        val fromElevation = elevation
        val toElevation = if (lifted) {
            resources.getDimension(R.dimen.design_appbar_elevation)
        } else {
            0F
        }
        if (fromElevation != toElevation) {
            ValueAnimator.ofFloat(fromElevation, toElevation).apply {
                addUpdateListener {
                    elevation = it.animatedValue as Float
                    (statusBarForeground as? MaterialShapeDrawable)?.elevation = it.animatedValue as Float
                }
                animators.add(this)
            }
        }

        val transparent = if (lifted) false else isTransparentWhenNotLifted
        val fromAlpha = (background as? MaterialShapeDrawable)?.alpha ?: background.alpha
        val toAlpha = if (transparent) 0 else 255
        if (fromAlpha != toAlpha) {
            ValueAnimator.ofInt(fromAlpha, toAlpha).apply {
                addUpdateListener {
                    val value = it.animatedValue as Int
                    background.alpha = value
                }
                animators.add(this)
            }
        }

        if (animators.isNotEmpty()) {
            animatorSet?.cancel()
            animatorSet = AnimatorSet().apply {
                duration = resources.getInteger(R.integer.app_bar_elevation_anim_duration).toLong()
                interpolator = AnimationUtils.LINEAR_INTERPOLATOR
                playTogether(*animators.toTypedArray())
                start()
            }
        }
    }

    init {
        statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)
    }
}
