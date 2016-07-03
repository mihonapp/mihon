package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Bundle
import android.support.v7.preference.PreferenceCategory
import android.support.v7.preference.XpPreferenceFragment
import android.view.View
import eu.kanade.tachiyomi.data.mangasync.MangaSyncManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.widget.preference.LoginPreference
import eu.kanade.tachiyomi.widget.preference.MangaSyncLoginDialog
import uy.kohesive.injekt.injectLazy

class SettingsSyncFragment : SettingsFragment() {

    companion object {
        const val SYNC_CHANGE_REQUEST = 121

        fun newInstance(rootKey: String): SettingsSyncFragment {
            val args = Bundle()
            args.putString(XpPreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
            return SettingsSyncFragment().apply { arguments = args }
        }
    }

    private val syncManager: MangaSyncManager by injectLazy()

    private val preferences: PreferencesHelper by injectLazy()

    val syncCategory by lazy { findPreference("pref_category_manga_sync_accounts") as PreferenceCategory }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        super.onViewCreated(view, savedState)

        val themedContext = preferenceManager.context

        for (sync in syncManager.services) {
            val pref = LoginPreference(themedContext).apply {
                key = preferences.keys.syncUsername(sync.id)
                title = sync.name

                setOnPreferenceClickListener {
                    val fragment = MangaSyncLoginDialog.newInstance(sync)
                    fragment.setTargetFragment(this@SettingsSyncFragment, SYNC_CHANGE_REQUEST)
                    fragment.show(fragmentManager, null)
                    true
                }
            }

            syncCategory.addPreference(pref)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SYNC_CHANGE_REQUEST) {
            val pref = findPreference(preferences.keys.syncUsername(resultCode)) as? LoginPreference
            pref?.notifyChanged()
        }
    }

}
