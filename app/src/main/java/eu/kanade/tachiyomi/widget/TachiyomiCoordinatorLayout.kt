package eu.kanade.tachiyomi.widget

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.R
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnLayout
import androidx.customview.view.AbsSavedState
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.viewpager.widget.ViewPager
import com.bluelinelabs.conductor.ChangeHandlerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.view.findChild
import eu.kanade.tachiyomi.util.view.findDescendant
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.HierarchyChangeEvent
import reactivecircus.flowbinding.android.view.hierarchyChangeEvents

/**
 * [CoordinatorLayout] with its own app bar lift state handler.
 * This parent view checks for the app bar lift state from the following:
 *
 * 1. When nested scroll detected, lift state will be decided from the nested
 * scroll target. (See [onNestedScroll])
 *
 * With those conditions, this view expects the following direct child:
 *
 * 1. An [AppBarLayout].
 *
 * 2. A [ChangeHandlerFrameLayout] that contains an optional [ViewPager].
 */
class TachiyomiCoordinatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.coordinatorLayoutStyle
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    /**
     * Keep lifted state and do nothing on tablet UI
     */
    private val isTablet = context.isTablet()

    private var appBarLayout: AppBarLayout? = null
    private var viewPager: ViewPager? = null

    /**
     * If true, [AppBarLayout] child will be lifted on nested scroll.
     */
    var isLiftAppBarOnScroll = true

    /**
     * Internal check
     */
    private val canLiftAppBarOnScroll
        get() = !isTablet && isLiftAppBarOnScroll

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
        // Disable elevation overlay when tabs are visible
        if (canLiftAppBarOnScroll && viewPager == null) {
            appBarLayout?.isLifted = dyConsumed != 0 || dyUnconsumed >= 0
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appBarLayout = findChild()
        viewPager = findChild<ChangeHandlerFrameLayout>()?.findDescendant()

        // Updates ViewPager reference when controller is changed
        findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.let { scope ->
            findChild<ChangeHandlerFrameLayout>()?.hierarchyChangeEvents()
                ?.onEach {
                    if (it is HierarchyChangeEvent.ChildRemoved) {
                        viewPager = (it.parent as? ViewGroup)?.findDescendant()
                    }
                }
                ?.launchIn(scope)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appBarLayout = null
        viewPager = null
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return if (superState != null) {
            SavedState(superState).also {
                it.appBarLifted = appBarLayout?.isLifted ?: false
            }
        } else {
            superState
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            doOnLayout {
                appBarLayout?.isLifted = state.appBarLifted
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : AbsSavedState {
        var appBarLifted = false

        constructor(superState: Parcelable) : super(superState)

        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            appBarLifted = source.readByte().toInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeByte((if (appBarLifted) 1 else 0).toByte())
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
}
