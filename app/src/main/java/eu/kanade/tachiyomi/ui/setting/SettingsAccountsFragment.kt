package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import android.os.Bundle
import android.support.v7.preference.DialogPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceCategory
import android.view.View
import eu.kanade.tachiyomi.R
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

    val sourceCategory by lazy { findPreference("pref_category_source_accounts") as PreferenceCategory }
    val syncCategory by lazy { findPreference("pref_category_manga_sync_accounts") as PreferenceCategory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val themedContext = preferenceManager.context

        for (source in getSourcesWithLogin()) {
            val pref = SourcePreference(themedContext).apply {
                isPersistent = false
                title = source.name
                key = source.id.toString()
                dialogLayoutResource = R.layout.pref_account_login
            }

            sourceCategory.addPreference(pref)
        }

        for (sync in settingsActivity.syncManager.services) {
            val pref = SyncPreference(themedContext).apply {
                isPersistent = false
                title = sync.name
                key = sync.id.toString()
                dialogLayoutResource = R.layout.pref_account_login
            }

            syncCategory.addPreference(pref)
        }
    }

    fun getSourcesWithLogin(): List<Source> {
        return settingsActivity.sourceManager.sources.filter { it.isLoginRequired }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is SourcePreference) {
            val fragment = SourceLoginDialog.newInstance(preference)
            fragment.setTargetFragment(this, 0)
            fragment.show(childFragmentManager, null)
        } else if (preference is SyncPreference) {
            val fragment = MangaSyncLoginDialog.newInstance(preference)
            fragment.setTargetFragment(this, 0)
            fragment.show(childFragmentManager, null)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    class SourcePreference(context: Context) : DialogPreference(context) {}

    class SyncPreference(context: Context) : DialogPreference(context) {}

}
