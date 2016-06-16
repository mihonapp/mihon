package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.XpPreferenceFragment
import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateAlarm
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.plusAssign
import eu.kanade.tachiyomi.widget.preference.IntListPreference
import eu.kanade.tachiyomi.widget.preference.LibraryColumnsDialog
import eu.kanade.tachiyomi.widget.preference.SimpleDialogPreference
import net.xpece.android.support.preference.MultiSelectListPreference
import rx.Observable
import uy.kohesive.injekt.injectLazy

class SettingsGeneralFragment : SettingsFragment(),
        PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {


    companion object {
        fun newInstance(rootKey: String): SettingsGeneralFragment {
            val args = Bundle()
            args.putString(XpPreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
            return SettingsGeneralFragment().apply { arguments = args }
        }
    }

    private val preferences: PreferencesHelper by injectLazy()


    val columnsPreference by lazy {
        findPreference(getString(R.string.pref_library_columns_dialog_key)) as SimpleDialogPreference
    }

    val updateInterval by lazy {
        findPreference(getString(R.string.pref_library_update_interval_key)) as IntListPreference
    }

    val updateRestriction by lazy {
        findPreference(getString(R.string.pref_library_update_restriction_key)) as MultiSelectListPreference
    }

    val themePreference by lazy {
        findPreference(getString(R.string.pref_theme_key)) as IntListPreference
    }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        super.onViewCreated(view, savedState)

        subscriptions += preferences.libraryUpdateInterval().asObservable()
                .subscribe { updateRestriction.isVisible = it > 0 }

        subscriptions += Observable.combineLatest(
                preferences.portraitColumns().asObservable(),
                preferences.landscapeColumns().asObservable())
                { portraitColumns, landscapeColumns -> Pair(portraitColumns, landscapeColumns) }
                .subscribe { updateColumnsSummary(it.first, it.second) }

        updateInterval.setOnPreferenceChangeListener { preference, newValue ->
            LibraryUpdateAlarm.startAlarm(activity, (newValue as String).toInt())
            true
        }

        themePreference.setOnPreferenceChangeListener { preference, newValue ->
            // Rebuild activity's to apply themes.
            TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(Intent(activity.intent))
                    .startActivities()
            true
        }
    }

    override fun onPreferenceDisplayDialog(p0: PreferenceFragmentCompat?, p: Preference): Boolean {
        if (p.key == getString(R.string.pref_library_columns_dialog_key)) {
            val fragment = LibraryColumnsDialog.newInstance(p)
            fragment.setTargetFragment(this, 0)
            fragment.show(childFragmentManager, null)
            return true
        }
        return false
    }

    private fun updateColumnsSummary(portraitColumns: Int, landscapeColumns: Int) {
        val portrait = getColumnValue(portraitColumns)
        val landscape = getColumnValue(landscapeColumns)
        val msg = "${getString(R.string.portrait)}: $portrait, ${getString(R.string.landscape)}: $landscape"

        columnsPreference.summary = msg
    }

    private fun getColumnValue(value: Int): String {
        return if (value == 0) getString(R.string.default_columns) else value.toString()
    }

}
