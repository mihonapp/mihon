package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.view.View
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.settings_search_controller_card.setting
import kotlinx.android.synthetic.main.settings_search_controller_card.title_wrapper

/**
 * Holder that binds the [SettingsSearchItem] containing catalogue cards.
 *
 * @param view view of [SettingsSearchItem]
 * @param adapter instance of [SettingsSearchAdapter]
 */
class SettingsSearchHolder(view: View, val adapter: SettingsSearchAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    /**
     * Adapter containing preference from search results.
     */
    private val settingsAdapter = SettingsSearchAdapter(adapter.controller)

    private var lastBoundResults: List<SettingsSearchItem>? = null

    init {
        title_wrapper.setOnClickListener {
            adapter.getItem(bindingAdapterPosition)?.let {
                adapter.titleClickListener.onTitleClick(it.pref)
            }
        }
    }

    /**
     * Show the loading of source search result.
     *
     * @param item item of card.
     */
    fun bind(item: SettingsSearchItem) {
        val preference = item.pref
        val results = item.results

        val titlePrefix = if (item.highlighted) "▶ " else ""

        // Set Title with country code if available.
        setting.text = titlePrefix + preference.key

        if (results !== lastBoundResults) {
            settingsAdapter.updateDataSet(results)
            lastBoundResults = results
        }
    }
}
