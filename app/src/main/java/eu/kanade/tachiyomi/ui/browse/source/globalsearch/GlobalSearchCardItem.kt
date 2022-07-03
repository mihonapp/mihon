package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R

class GlobalSearchCardItem(val manga: Manga) : AbstractFlexibleItem<GlobalSearchCardHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.global_search_controller_card_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): GlobalSearchCardHolder {
        return GlobalSearchCardHolder(view, adapter as GlobalSearchCardAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: GlobalSearchCardHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (other is GlobalSearchCardItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id.hashCode()
    }
}
