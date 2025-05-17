package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.os.Parcelable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.parcelize.Parcelize
import tachiyomi.domain.source.service.SourceManager

class MigrationSourceItem(
    val source: HttpSource,
    var sourceEnabled: Boolean,
) : AbstractFlexibleItem<MigrationSourceHolder>() {
    override fun getLayoutRes() = R.layout.migration_source_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): MigrationSourceHolder {
        return MigrationSourceHolder(view, adapter as MigrationSourceAdapter)
    }

    /**
     * Binds the given view holder with this item.
     *
     * @param adapter The adapter of this item.
     * @param holder The holder to bind.
     * @param position The position of this item in the adapter.
     * @param payloads List of partial changes.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: MigrationSourceHolder,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.bind(source, sourceEnabled)
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is MigrationSourceItem) {
            return source.id == other.source.id
        }
        return false
    }

    override fun hashCode(): Int {
        return source.id.hashCode()
    }

    @Parcelize
    data class MigrationSource(val sourceId: Long, val sourceEnabled: Boolean) : Parcelable

    fun asParcelable(): MigrationSource {
        return MigrationSource(source.id, sourceEnabled)
    }

    companion object {
        fun fromParcelable(sourceManager: SourceManager, migrationSource: MigrationSource): MigrationSourceItem? {
            val source = sourceManager.get(migrationSource.sourceId) as? HttpSource ?: return null

            return MigrationSourceItem(
                source,
                migrationSource.sourceEnabled,
            )
        }
    }
}
