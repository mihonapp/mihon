package eu.kanade.tachiyomi.ui.recent.updates

import android.content.Context
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.chapter.base.BaseChaptersAdapter
import eu.kanade.tachiyomi.util.system.getResourceColor

class UpdatesAdapter(
    val controller: UpdatesController,
    context: Context,
    val items: List<IFlexible<*>>?,
) : BaseChaptersAdapter<IFlexible<*>>(controller, items) {

    var readColor = context.getResourceColor(R.attr.colorOnSurface, 0.38f)
    var unreadColor = context.getResourceColor(R.attr.colorOnSurface)
    val unreadColorSecondary = context.getResourceColor(android.R.attr.textColorSecondary)
    var bookmarkedColor = context.getResourceColor(R.attr.colorAccent)

    val coverClickListener: OnCoverClickListener = controller

    init {
        setDisplayHeadersAtStartUp(true)
    }

    interface OnCoverClickListener {
        fun onCoverClick(position: Int)
    }
}
