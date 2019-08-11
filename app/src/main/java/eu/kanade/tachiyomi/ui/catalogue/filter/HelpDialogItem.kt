package eu.kanade.tachiyomi.ui.catalogue.filter

import android.annotation.SuppressLint
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import io.noties.markwon.Markwon
import uy.kohesive.injekt.injectLazy

class HelpDialogItem(val filter: Filter.HelpDialog) : AbstractHeaderItem<HelpDialogItem.Holder>() {
    private val markwon: Markwon by injectLazy()

    @SuppressLint("PrivateResource")
    override fun getLayoutRes(): Int {
        return R.layout.navigation_view_help_dialog
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: List<Any?>?) {
        val view = holder.button as TextView
        view.text = filter.name
        view.setOnClickListener {
            val v = TextView(view.context)

            val parsed = markwon.parse(filter.markdown)
            val rendered = markwon.render(parsed)
            markwon.setParsedMarkdown(v, rendered)

            MaterialDialog.Builder(view.context)
                    .title(filter.dialogTitle)
                    .customView(v, true)
                    .positiveText("Ok")
                    .show()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return filter == (other as HelpDialogItem).filter
    }

    override fun hashCode(): Int {
        return filter.hashCode()
    }

    class Holder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {
        val button: Button = itemView.findViewById(R.id.dialog_open_button)
    }
}
