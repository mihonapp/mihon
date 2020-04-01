package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.switchPreference

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
