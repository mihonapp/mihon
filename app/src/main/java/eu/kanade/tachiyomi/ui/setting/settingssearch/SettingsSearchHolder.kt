package eu.kanade.tachiyomi.ui.setting.settingssearch

import android.view.View
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlin.reflect.full.createInstance
import kotlinx.android.synthetic.main.settings_search_controller_card.*

/**
 * Holder that binds the [SettingsSearchItem] containing catalogue cards.
 *
 * @param view view of [SettingsSearchItem]
 * @param adapter instance of [SettingsSearchAdapter]
 */
class SettingsSearchHolder(view: View, val adapter: SettingsSearchAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    init {
        title_wrapper.setOnClickListener {
            adapter.getItem(bindingAdapterPosition)?.let {
                val ctrl = it.settingsSearchResult.searchController::class.createInstance()
                ctrl.preferenceKey = it.settingsSearchResult.key

                // must pass a new Controller instance to avoid this error https://github.com/bluelinelabs/Conductor/issues/446
                adapter.titleClickListener.onTitleClick(ctrl)
            }
        }
    }

    /**
     * Show the loading of source search result.
     *
     * @param item item of card.
     */
    fun bind(item: SettingsSearchItem) {
        search_result_pref_title.text = item.settingsSearchResult.title
        search_result_pref_summary.text = item.settingsSearchResult.summary
        search_result_pref_breadcrumb.text = item.settingsSearchResult.breadcrumb
    }
}
