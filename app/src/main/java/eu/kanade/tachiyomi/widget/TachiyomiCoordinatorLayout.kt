package eu.kanade.tachiyomi.widget

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.R
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.customview.view.AbsSavedState
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import eu.kanade.tachiyomi.util.system.isTabletUi
import eu.kanade.tachiyomi.util.view.findChild

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
 */
class TachiyomiCoordinatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.coordinatorLayoutStyle,
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private var appBarLayout: AppBarLayout? = null
    private var tabLayout: TabLayout? = null

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
        // Disable elevation overlay when tabs are visible
        if (context.isTabletUi().not()) {
            if (target is ComposeView) {
                val scrollCondition = if (type == ViewCompat.TYPE_NON_TOUCH) {
                    dyUnconsumed >= 0
                } else {
                    dyConsumed != 0 || dyUnconsumed >= 0
                }
                appBarLayout?.isLifted = scrollCondition && tabLayout?.isVisible == false
            } else {
                appBarLayout?.isLifted = (dyConsumed != 0 || dyUnconsumed >= 0) && tabLayout?.isVisible == false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appBarLayout = findChild()
        tabLayout = appBarLayout?.findChild()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appBarLayout = null
        tabLayout = null
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
