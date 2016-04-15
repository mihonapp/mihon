package eu.kanade.tachiyomi.widget

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet

/**
 * No Predictive Animations LinearLayoutManager
 */
open class NpaLinearLayoutManager : LinearLayoutManager {

    constructor(context: Context): super(context) {}

    constructor(context: Context, orientation: Int, reverseLayout: Boolean)
        : super(context, orientation, reverseLayout) {}

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attrs, defStyleAttr, defStyleRes) {}

    /**
     * Disable predictive animations. There is a bug in RecyclerView which causes views that
     * are being reloaded to pull invalid ViewHolders from the internal recycler stack if the
     * adapter size has decreased since the ViewHolder was recycled.
     */
    override fun supportsPredictiveItemAnimations() = false

}