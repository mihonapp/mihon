package eu.kanade.tachiyomi.ui.setting.database

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ClearDatabaseSourceItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.icon

data class ClearDatabaseSourceItem(val source: Source, private val mangaCount: Long) : AbstractFlexibleItem<ClearDatabaseSourceItem.Holder>() {

    override fun getLayoutRes(): Int {
        return R.layout.clear_database_source_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?, holder: Holder?, position: Int, payloads: MutableList<Any>?) {
        holder?.bind(source, mangaCount)
    }

    class Holder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {

        private val binding = ClearDatabaseSourceItemBinding.bind(view)

        fun bind(source: Source, count: Long) {
            binding.title.text = source.toString()
            binding.description.text = itemView.context.getString(R.string.clear_database_source_item_count, count)

            itemView.post {
                when {
                    source.icon() != null && source.id != LocalSource.ID ->
                        binding.thumbnail.setImageDrawable(source.icon())
                    else -> binding.thumbnail.setImageResource(R.mipmap.ic_local_source)
                }
            }

            binding.checkbox.isChecked = (bindingAdapter as FlexibleAdapter<*>).isSelected(bindingAdapterPosition)
        }
    }
}
