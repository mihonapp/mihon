package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * A custom layout that splits space evenly between flexible children,
 * while respecting fixed-width children (like hinge gaps).
 *
 * Used for the side-by-side reader to guarantee strict 50/50 page splits
 * without the centering/collapsing issues of LinearLayout weights.
 */
open class SideBySideLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var centerSingleChild: Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        
        setMeasuredDimension(width, height)

        var fixedWidth = 0
        val flexibleChildren = mutableListOf<View>()

        // Pass 1: Identify roles
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            
            val lp = child.layoutParams
            if (lp.width > 0) {
                fixedWidth += lp.width
            } else {
                flexibleChildren.add(child)
            }
        }

        // Pass 2: Measure fixed
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams
            if (lp.width > 0) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
            }
        }

        // Pass 3: Measure flexible (Assume 2 columns)
        if (flexibleChildren.isNotEmpty() && width > 0) {
            val remainingWidth = (width - fixedWidth).coerceAtLeast(0)
            // Force divide by 2 if we have 1 or 2 flexible children (guarantees 50% split)
            val columns = if (flexibleChildren.size <= 2) 2 else flexibleChildren.size
            val childWidth = remainingWidth / columns
            
            val widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)

            for (child in flexibleChildren) {
                child.measure(widthSpec, heightSpec)
            }
        } else if (flexibleChildren.isNotEmpty()) {
            // Width is 0 (initial pass), measure with 0
            val zeroSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)
            for (child in flexibleChildren) {
                child.measure(zeroSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        var currentX = 0
        
        val visibleChildren = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        
        if (visibleChildren.size == 1 && centerSingleChild) {
            val child = visibleChildren[0]
            val childWidth = child.measuredWidth
            val startX = (width - childWidth) / 2
            child.layout(startX, 0, startX + childWidth, height)
            return
        }

        for (child in visibleChildren) {
            val childWidth = child.measuredWidth
            child.layout(currentX, 0, currentX + childWidth, height)
            currentX += childWidth
        }
    }
}
