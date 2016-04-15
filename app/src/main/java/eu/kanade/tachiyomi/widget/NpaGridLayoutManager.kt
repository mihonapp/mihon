package eu.kanade.tachiyomi.widget

import android.content.Context
import android.support.v7.widget.GridLayoutManager
import android.util.AttributeSet

/**
 * No Predictive Animations GridLayoutManager
 */
open class NpaGridLayoutManager : GridLayoutManager {

    constructor(context: Context, spanCount: Int) : super(context, spanCount) {}

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attrs, defStyleAttr, defStyleRes) {}

    constructor(context: Context, spanCount: Int, orientation: Int, reverseLayout: Boolean)
        : super(context, spanCount, orientation, reverseLayout) {}

    /**
     * Disable predictive animations. There is a bug in RecyclerView which causes views that
     * are being reloaded to pull invalid ViewHolders from the internal recycler stack if the
     * adapter size has decreased since the ViewHolder was recycled.
     */
    override fun supportsPredictiveItemAnimations() = false

}