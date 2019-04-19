package eu.kanade.tachiyomi.ui.setting

import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.source.SourceManager
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

        switchPreference {
            title = "Use high-quality thumbnails"
            summary = "May slow down search results"
            key = PreferenceKeys.eh_hl_useHighQualityThumbs
            defaultValue = false
        }
    }
}
