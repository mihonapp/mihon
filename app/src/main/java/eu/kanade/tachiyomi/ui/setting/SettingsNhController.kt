package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.switchPreference

/**
 * nhentai Settings fragment
 */

class SettingsNhController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        title = "nhentai"

        switchPreference {
            title = "Use high-quality thumbnails"
            summary = "May slow down search results"
            key = PreferenceKeys.eh_nh_useHighQualityThumbs
            defaultValue = false
        }
    }
}
