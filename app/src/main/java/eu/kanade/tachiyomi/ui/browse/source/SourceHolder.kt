package eu.kanade.tachiyomi.ui.browse.source

import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.holder.SlicedHolder
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import exh.EH_SOURCE_ID
import exh.EIGHTMUSES_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.HBROWSE_SOURCE_ID
import exh.HITOMI_SOURCE_ID
import exh.MERGED_SOURCE_ID
import exh.NHENTAI_SOURCE_ID
import exh.PERV_EDEN_EN_SOURCE_ID
import exh.PERV_EDEN_IT_SOURCE_ID
import io.github.mthli.slice.Slice
import kotlinx.android.synthetic.main.source_main_controller_card_item.card
import kotlinx.android.synthetic.main.source_main_controller_card_item.image
import kotlinx.android.synthetic.main.source_main_controller_card_item.source_browse
import kotlinx.android.synthetic.main.source_main_controller_card_item.source_latest
import kotlinx.android.synthetic.main.source_main_controller_card_item.title

class SourceHolder(view: View, override val adapter: SourceAdapter, val showButtons: Boolean) :
    BaseFlexibleViewHolder(view, adapter),
    SlicedHolder {

    override val slice = Slice(card).apply {
        setColor(adapter.cardBackground)
    }

    override val viewToSlice: View
        get() = card

    init {
        source_browse.setOnClickListener {
            adapter.browseClickListener.onBrowseClick(bindingAdapterPosition)
        }

        source_latest.setOnClickListener {
            adapter.latestClickListener.onLatestClick(bindingAdapterPosition)
        }

        if (!showButtons) {
            source_browse.gone()
            source_latest.gone()
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source
        setCardEdges(item)

        // Set source name
        title.text = source.name

        // Set circle letter image.
        itemView.post {
            val icon = source.icon()
            when {
                icon != null -> image.setImageDrawable(icon)
                item.source.id == LocalSource.ID -> image.setImageResource(R.mipmap.ic_local_source)
                item.source.id == EH_SOURCE_ID -> image.setImageResource(R.mipmap.ic_ehentai_source)
                item.source.id == EXH_SOURCE_ID -> image.setImageResource(R.mipmap.ic_ehentai_source)
                item.source.id == PERV_EDEN_EN_SOURCE_ID -> image.setImageResource(R.mipmap.ic_perveden_source)
                item.source.id == PERV_EDEN_IT_SOURCE_ID -> image.setImageResource(R.mipmap.ic_perveden_source)
                item.source.id == NHENTAI_SOURCE_ID -> image.setImageResource(R.mipmap.ic_nhentai_source)
                item.source.id == HITOMI_SOURCE_ID -> image.setImageResource(R.mipmap.ic_hitomi_source)
                item.source.id == EIGHTMUSES_SOURCE_ID -> image.setImageResource(R.mipmap.ic_8muses_source)
                item.source.id == HBROWSE_SOURCE_ID -> image.setImageResource(R.mipmap.ic_hbrowse_source)
                item.source.id == MERGED_SOURCE_ID -> image.setImageResource(R.mipmap.ic_merged_source)
            }
        }

        source_browse.setText(R.string.browse)
        if (source.supportsLatest && showButtons) {
            source_latest.visible()
        } else {
            source_latest.gone()
        }
    }
}
