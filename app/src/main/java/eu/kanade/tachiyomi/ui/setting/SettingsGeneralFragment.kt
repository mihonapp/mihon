package eu.kanade.tachiyomi.ui.setting

import android.os.Bundle
import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateAlarm
import eu.kanade.tachiyomi.widget.preference.IntListPreference
import eu.kanade.tachiyomi.widget.preference.LibraryColumnsDialog

class SettingsGeneralFragment : SettingsNestedFragment() {

    companion object {
        fun newInstance(resourcePreference: Int, resourceTitle: Int): SettingsGeneralFragment {
            val fragment = SettingsGeneralFragment();
            fragment.setArgs(resourcePreference, resourceTitle);
            return fragment;
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val columnsDialog = findPreference(
                getString(R.string.pref_library_columns_dialog_key)) as LibraryColumnsDialog

        columnsDialog.setPreferencesHelper(preferences)

        val updateInterval = findPreference(
                getString(R.string.pref_library_update_interval_key)) as IntListPreference

        updateInterval.setOnPreferenceChangeListener { preference, newValue ->
            LibraryUpdateAlarm.startAlarm(activity, (newValue as String).toInt())
            true
        }
    }

}
