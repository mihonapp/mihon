package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import android.preference.PreferenceCategory
import android.view.View
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.widget.preference.MangaSyncLoginDialog
import eu.kanade.tachiyomi.widget.preference.SourceLoginDialog

class SettingsAccountsFragment : SettingsNestedFragment() {

    companion object {

        fun newInstance(resourcePreference: Int, resourceTitle: Int): SettingsNestedFragment {
            val fragment = SettingsAccountsFragment()
            fragment.setArgs(resourcePreference, resourceTitle)
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        PreferenceCategory(activity).apply {
            preferenceScreen.addPreference(this)
            title = "Sources"

            for (source in getSourcesWithLogin()) {
                val dialog = SourceLoginDialog(activity, preferences, source)
                dialog.title = source.name

                addPreference(dialog)
            }
        }

        PreferenceCategory(activity).apply {
            preferenceScreen.addPreference(this)
            title = "Sync"

            for (sync in settingsActivity.syncManager.services) {
                val dialog = MangaSyncLoginDialog(activity, preferences, sync)
                dialog.title = sync.name

                addPreference(dialog)
            }
        }
    }

    fun getSourcesWithLogin(): List<Source> {
        return settingsActivity.sourceManager.sources.filter { it.isLoginRequired }
    }

}
