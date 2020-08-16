package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.view.View
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

/**
 * Item that contains search result information.
 *
 * @param pref the source for the search results.
 * @param results the search results.
 * @param highlighted whether this search item should be highlighted/marked in the catalogue search view.
 */
class SettingsSearchItem(val pref: Preference, val results: List<SettingsSearchItem>?, val highlighted: Boolean = false) :
    AbstractFlexibleItem<SettingsSearchHolder>() {

    /**
     * Set view.
     *
     * @return id of view
     */
    override fun getLayoutRes(): Int {
        return R.layout.settings_search_controller_card
    }

    /**
     * Create view holder (see [SettingsSearchAdapter].
     *
     * @return holder of view.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): SettingsSearchHolder {
        return SettingsSearchHolder(view, adapter as SettingsSearchAdapter)
    }

    /**
     * Bind item to view.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SettingsSearchHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.bind(this)
    }

    /**
     * Used to check if two items are equal.
     *
     * @return items are equal?
     */
    override fun equals(other: Any?): Boolean {
        if (other is SettingsSearchItem) {
            return pref.key == other.pref.key
        }
        return false
    }

    /**
     * Return hash code of item.
     *
     * @return hashcode
     */
    override fun hashCode(): Int {
        return pref.key.toInt()
    }
}
