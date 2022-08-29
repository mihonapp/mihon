package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.fredporciuncula.flow.preferences.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceComfortableGridItemBinding
import eu.kanade.tachiyomi.databinding.SourceCompactGridItemBinding
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode

class SourceItem(val manga: Manga, private val displayMode: Preference<LibraryDisplayMode>) :
    AbstractFlexibleItem<SourceHolder<*>>() {

    override fun getLayoutRes(): Int {
        return when (displayMode.get()) {
            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> R.layout.source_compact_grid_item
            LibraryDisplayMode.ComfortableGrid -> R.layout.source_comfortable_grid_item
            LibraryDisplayMode.List -> R.layout.source_list_item
        }
    }

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): SourceHolder<*> {
        return when (displayMode.get()) {
            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                SourceCompactGridHolder(SourceCompactGridItemBinding.bind(view), adapter)
            }
            LibraryDisplayMode.ComfortableGrid -> {
                SourceComfortableGridHolder(SourceComfortableGridItemBinding.bind(view), adapter)
            }
            LibraryDisplayMode.List -> {
                SourceListHolder(view, adapter)
            }
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceHolder<*>,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.onSetValues(manga)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SourceItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id.hashCode()
    }
}
