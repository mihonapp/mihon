package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceGroup
import android.support.v7.preference.XpPreferenceFragment
import android.view.View
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.Source
import eu.kanade.tachiyomi.data.source.SourceManager
import eu.kanade.tachiyomi.data.source.getLanguages
import eu.kanade.tachiyomi.data.source.online.LoginSource
import eu.kanade.tachiyomi.util.plusAssign
import eu.kanade.tachiyomi.widget.preference.LoginPreference
import eu.kanade.tachiyomi.widget.preference.SourceLoginDialog
import net.xpece.android.support.preference.MultiSelectListPreference
import uy.kohesive.injekt.injectLazy

class SettingsSourcesFragment : SettingsFragment() {

    companion object {
        const val SOURCE_CHANGE_REQUEST = 120

        fun newInstance(rootKey: String?): SettingsSourcesFragment {
            val args = Bundle()
            args.putString(XpPreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
            return SettingsSourcesFragment().apply { arguments = args }
        }
    }

    private val preferences: PreferencesHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    val languagesPref by lazy { findPreference("pref_source_languages") as MultiSelectListPreference }

    val sourcesPref by lazy { findPreference("pref_sources") as PreferenceGroup }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        super.onViewCreated(view, savedState)

        val langs = getLanguages().sortedBy { it.lang }

        val entryKeys = langs.map { it.code }
        languagesPref.entries = langs.map { it.lang }.toTypedArray()
        languagesPref.entryValues = entryKeys.toTypedArray()
        languagesPref.values = preferences.enabledLanguages().getOrDefault()

        subscriptions += preferences.enabledLanguages().asObservable()
                .subscribe { languages ->
                    sourcesPref.removeAll()

                    val enabledSources = sourceManager.getOnlineSources()
                            .filter { it.lang.code in languages }

                    for (source in enabledSources.filterIsInstance(LoginSource::class.java)) {
                        val pref = createLoginSourceEntry(source)
                        sourcesPref.addPreference(pref)
                    }

                    // Hide category if it doesn't have any child
                    sourcesPref.isVisible = sourcesPref.preferenceCount > 0
                }
    }

    fun createLoginSourceEntry(source: Source): Preference {
        return LoginPreference(preferenceManager.context).apply {
            key = preferences.keys.sourceUsername(source.id)
            title = source.toString()

            setOnPreferenceClickListener {
                val fragment = SourceLoginDialog.newInstance(source)
                fragment.setTargetFragment(this@SettingsSourcesFragment, SOURCE_CHANGE_REQUEST)
                fragment.show(fragmentManager, null)
                true
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SOURCE_CHANGE_REQUEST) {
            val pref = findPreference(preferences.keys.sourceUsername(resultCode)) as? LoginPreference
            pref?.notifyChanged()
        }
    }

}
