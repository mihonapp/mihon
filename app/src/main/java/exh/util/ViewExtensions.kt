package exh.util

import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.Px
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.HITOMI_SOURCE_ID
import exh.NHENTAI_SOURCE_ID

inline val View.marginTop: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

inline val View.marginBottom: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

inline val View.marginRight: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0

inline val View.marginLeft: Int
    get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0

fun View.doOnApplyWindowInsets(f: (View, WindowInsets, ViewPaddingState) -> Unit) {
    // Create a snapshot of the view's padding state
    val paddingState = createStateForView(this)
    setOnApplyWindowInsetsListener { v, insets ->
        f(v, insets, paddingState)
        insets
    }
    requestApplyInsetsWhenAttached()
}

object ControllerViewWindowInsetsListener : View.OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        v.updateLayoutParams<FrameLayout.LayoutParams> {
            val attrsArray = intArrayOf(android.R.attr.actionBarSize)
            val array = v.context.obtainStyledAttributes(attrsArray)
            topMargin = insets.systemWindowInsetTop + array.getDimensionPixelSize(0, 0)
            array.recycle()
        }
        return insets
    }
}

fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParams(block: T.() -> Unit) {
    val params = layoutParams as T
    block(params)
    layoutParams = params
}

fun View.applyWindowInsetsForController() {
    setOnApplyWindowInsetsListener(ControllerViewWindowInsetsListener)
    requestApplyInsetsWhenAttached()
}

inline fun View.updatePaddingRelative(
    @Px start: Int = paddingStart,
    @Px top: Int = paddingTop,
    @Px end: Int = paddingEnd,
    @Px bottom: Int = paddingBottom
) {
    setPaddingRelative(start, top, end, bottom)
}

private fun createStateForView(view: View) = ViewPaddingState(
    view.paddingLeft,
    view.paddingTop,
    view.paddingRight,
    view.paddingBottom,
    view.paddingStart,
    view.paddingEnd
)

data class ViewPaddingState(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val start: Int,
    val end: Int
)

object RecyclerWindowInsetsListener : View.OnApplyWindowInsetsListener {
    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
        v.updatePaddingRelative(bottom = insets.systemWindowInsetBottom)
        // v.updatePaddingRelative(bottom = v.paddingBottom + insets.systemWindowInsetBottom)
        return insets
    }
}

/**
 * Replaces chips in a ChipGroup.
 *
 * @param items List of strings that are shown as individual chips.
 * @param onClick Optional on click listener for each chip.
 * @param onLongClick Optional long click listener for each chip.
 * @param sourceId Optional source check to determine if we need special search functions for each chip.
 */
fun ChipGroup.setChipsExtended(items: List<String>?, onClick: (item: String) -> Unit = {}, onLongClick: (item: String) -> Unit = {}, sourceId: Long = 0L) {
    removeAllViews()

    items?.forEach { item ->
        val chip = Chip(context).apply {
            text = item
            var search = item
            if (sourceId == EXH_SOURCE_ID || sourceId == EH_SOURCE_ID || sourceId == NHENTAI_SOURCE_ID || sourceId == HITOMI_SOURCE_ID) {
                val parsed = parseTag(search)
                search = wrapTag(parsed.first, parsed.second.substringBefore('|').trim())
            }
            setOnClickListener { onClick(search) }
            setOnLongClickListener {
                onLongClick(search)
                false
            }
        }

        addView(chip)
    }
}

private fun parseTag(tag: String) = tag.substringBefore(':').trim() to tag.substringAfter(':').trim()

private fun wrapTag(namespace: String, tag: String) = if (tag.contains(' ')) {
    "$namespace:\"$tag$\""
} else {
    "$namespace:$tag$"
}
