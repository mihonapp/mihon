package exh.ui.migration.manga.design

import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.getRound
import kotlinx.android.synthetic.main.eh_source_item.*
import android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

class MigrationSourceHolder(view: View, val adapter: FlexibleAdapter<MigrationSourceItem>):
        BaseFlexibleViewHolder(view, adapter) {
    init {
        setDragHandleView(reorder)
    }

    fun bind(source: HttpSource, sourceEnabled: Boolean) {
        // Set capitalized title.
        title.text = source.name.capitalize()

        // Update circle letter image.
        itemView.post {
            image.setImageDrawable(image.getRound(source.name.take(1).toUpperCase(),false))
        }

        if(sourceEnabled) {
            title.alpha = 1.0f
            image.alpha = 1.0f
            title.paintFlags = title.paintFlags and STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            title.alpha = DISABLED_ALPHA
            image.alpha = DISABLED_ALPHA
            title.paintFlags = title.paintFlags or STRIKE_THRU_TEXT_FLAG
        }
    }

    companion object {
        private const val DISABLED_ALPHA = 0.3f
    }
}