package eu.kanade.tachiyomi.ui.more.licenses

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.aboutlibraries.entity.Library
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

class LicensesItem(val library: Library) : AbstractFlexibleItem<LicensesHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.licenses_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LicensesHolder {
        return LicensesHolder(view, adapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: LicensesHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.bind(library)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is LicensesItem) {
            return library.hashCode() == other.hashCode()
        }
        return false
    }

    override fun hashCode(): Int {
        return library.hashCode()
    }
}
