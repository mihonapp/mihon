package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

class MigrationProcessItem(val manga: MigratingManga) :
    AbstractFlexibleItem<MigrationProcessHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.migration_process_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): MigrationProcessHolder {
        return MigrationProcessHolder(view, adapter as MigrationProcessAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: MigrationProcessHolder,
        position: Int,
        payloads: MutableList<Any?>?
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is MigrationProcessItem) {
            return manga.mangaId == other.manga.mangaId
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.mangaId.toInt()
    }
}
