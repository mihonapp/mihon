package eu.kanade.tachiyomi.ui.base.holder

import android.view.View
import kotlinx.android.extensions.LayoutContainer

abstract class BaseViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view), LayoutContainer {

    override val containerView: View?
        get() = itemView
}