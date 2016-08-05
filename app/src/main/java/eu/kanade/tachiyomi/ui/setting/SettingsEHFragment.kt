package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import android.support.v7.preference.XpPreferenceFragment

class SettingsEHFragment : SettingsFragment() {
    companion object {
        fun newInstance(rootKey: String): SettingsEHFragment {
            val args = Bundle()
            args.putString(XpPreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
            return SettingsEHFragment().apply { arguments = args }
        }
    }
}
