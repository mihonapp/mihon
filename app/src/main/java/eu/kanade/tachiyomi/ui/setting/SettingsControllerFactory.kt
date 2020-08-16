package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import com.bytehamster.lib.preferencesearch.SearchPreference
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsControllerFactory(context: Context) {
    var searchablePrefs = Keys::class.members.map { member -> SearchPreference(context).key = member.name }

    companion object Factory {
        var controllers: List<SettingsController>? = null
    }
}
