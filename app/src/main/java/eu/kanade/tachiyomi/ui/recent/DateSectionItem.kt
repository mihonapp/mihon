package eu.kanade.tachiyomi.ui.recent

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SectionHeaderItemBinding
import eu.kanade.tachiyomi.util.lang.toRelativeString
import java.text.DateFormat
import java.util.Date

class DateSectionItem(
    private val date: Date,
    private val range: Int,
    private val dateFormat: DateFormat,
) : AbstractHeaderItem<DateSectionItem.DateSectionItemHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.section_header_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): DateSectionItemHolder {
        return DateSectionItemHolder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: DateSectionItemHolder, position: Int, payloads: List<Any?>?) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is DateSectionItem) {
            return date == other.date
        }
        return false
    }

    override fun hashCode(): Int {
        return date.hashCode()
    }

    inner class DateSectionItemHolder(private val view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter, true) {

        private val binding = SectionHeaderItemBinding.bind(view)

        fun bind(item: DateSectionItem) {
            binding.title.text = item.date.toRelativeString(view.context, range, dateFormat)
        }
    }
}
