package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.recyclerview.widget.RecyclerView

abstract class WebtoonBaseHolder(
    view: View,
    protected val viewer: WebtoonViewer,
) : RecyclerView.ViewHolder(view) {

    /**
     * Context getter because it's used often.
     */
    val context: Context get() = itemView.context

    /**
     * Called when the view is recycled and being added to the view pool.
     */
    open fun recycle() {}

    /**
     * Extension method to set layout params to wrap content on this view.
     */
    protected fun View.wrapContent() {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
}
