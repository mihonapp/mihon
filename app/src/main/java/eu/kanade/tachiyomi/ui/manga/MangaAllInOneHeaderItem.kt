package eu.kanade.tachiyomi.ui.manga

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.SourceController

class MangaAllInOneHeaderItem(val manga: Manga, val source: Source, var smartSearchConfig: SourceController.SmartSearchConfig? = null) :
    AbstractFlexibleItem<MangaAllInOneHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.manga_all_in_one_header
    }

    override fun isSelectable(): Boolean {
        return false
    }

    override fun isSwipeable(): Boolean {
        return false
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): MangaAllInOneHolder {
        return MangaAllInOneHolder(view, adapter as MangaAllInOneAdapter, smartSearchConfig)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: MangaAllInOneHolder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {
        holder.bind(this, manga, source)
    }

    override fun equals(other: Any?): Boolean {
        return (this === other)
    }

    override fun hashCode(): Int {
        return -(manga.id).hashCode()
    }
}
