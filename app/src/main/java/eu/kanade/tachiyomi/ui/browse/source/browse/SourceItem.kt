package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.fredporciuncula.flow.preferences.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.SourceComfortableGridItemBinding
import eu.kanade.tachiyomi.databinding.SourceCompactGridItemBinding
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting

class SourceItem(val manga: Manga, private val displayMode: Preference<DisplayModeSetting>) :
    AbstractFlexibleItem<SourceHolder<*>>() {

    override fun getLayoutRes(): Int {
        return when (displayMode.get()) {
            DisplayModeSetting.COMPACT_GRID, DisplayModeSetting.COVER_ONLY_GRID -> R.layout.source_compact_grid_item
            DisplayModeSetting.COMFORTABLE_GRID -> R.layout.source_comfortable_grid_item
            DisplayModeSetting.LIST -> R.layout.source_list_item
        }
    }

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): SourceHolder<*> {
        return when (displayMode.get()) {
            DisplayModeSetting.COMPACT_GRID, DisplayModeSetting.COVER_ONLY_GRID -> {
                SourceCompactGridHolder(SourceCompactGridItemBinding.bind(view), adapter)
            }
            DisplayModeSetting.COMFORTABLE_GRID -> {
                SourceComfortableGridHolder(SourceComfortableGridItemBinding.bind(view), adapter)
            }
            DisplayModeSetting.LIST -> {
                SourceListHolder(view, adapter)
            }
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceHolder<*>,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.onSetValues(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SourceItem) {
            return manga.id!! == other.manga.id!!
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id!!.hashCode()
    }
}
