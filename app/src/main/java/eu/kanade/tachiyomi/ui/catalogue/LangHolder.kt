package eu.kanade.tachiyomi.ui.catalogue

import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.catalogue_main_controller_card.*
import java.util.*

class LangHolder(view: View, adapter: FlexibleAdapter<*>) :
        BaseFlexibleViewHolder(view, adapter, true) {

    fun bind(item: LangItem) {
        title.text = when {
            item.code == "" -> itemView.context.getString(R.string.other_source)
            else -> {
                val locale = Locale(item.code)
                locale.getDisplayName(locale).capitalize()
            }
        }
    }
}