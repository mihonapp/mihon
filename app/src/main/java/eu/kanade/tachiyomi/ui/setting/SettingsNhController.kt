package eu.kanade.tachiyomi.ui.setting

import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.preference.PreferenceKeys

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
