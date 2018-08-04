package eu.kanade.tachiyomi.ui.setting

import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.util.toast
import exh.HITOMI_SOURCE_ID
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * hitomi.la Settings fragment
 */

class SettingsHlController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "hitomi.la"

        editTextPreference {
            title = "Search database refresh frequency"
            summary = "How often to get new entries for the search database in hours. Setting this frequency too high may cause high CPU usage and network usage."
            key = PreferenceKeys.eh_hl_refreshFrequency
            defaultValue = "24"

            onChange {
                it as String

                if((it.toLongOrNull() ?: -1) <= 0) {
                    context.toast("Invalid frequency. Frequency must be a positive whole number.")
                    false
                } else true
            }
        }

        switchPreference {
            title = "Begin refreshing search database on app launch"
            summary = "Normally the search database gets refreshed (if required) when you open the hitomi.la catalogue. If you enable this option, the database gets refreshed in the background as soon as you open the app. It will result in higher data usage but may increase hitomi.la search speeds."
            key = PreferenceKeys.eh_hl_earlyRefresh
            defaultValue = false
        }

        preference {
            title = "Force refresh search database now"
            summary = "Delete the local copy of the hitomi.la search database and download the new database now. Hitomi.la search will not work in the ~10mins that it takes to refresh the search database"
            isPersistent = false

            onClick {
                context.toast(if((Injekt.get<SourceManager>().get(HITOMI_SOURCE_ID) as Hitomi).forceEnsureCacheLoaded()) {
                    "Refreshing database. You will NOT be notified when it is complete!"
                } else {
                    "Could not begin refresh process as there is already one ongoing!"
                }, Toast.LENGTH_LONG)
            }
        }
    }
}
