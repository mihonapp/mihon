package exh.debug

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

enum class DebugToggles(val default: Boolean) {
    ENABLE_EXH_ROOT_REDIRECT(true),
    ENABLE_DEBUG_OVERLAY(true),
    PULL_TO_ROOT_WHEN_LOADING_EXH_MANGA_DETAILS(true);

    val prefKey = "eh_debug_toggle_${name.toLowerCase()}"

    var enabled: Boolean
        get() = prefs.rxPrefs.getBoolean(prefKey, default).get()!!
        set(value) {
            prefs.rxPrefs.getBoolean(prefKey).set(value)
        }

    companion object {
        private val prefs: PreferencesHelper by injectLazy()
    }
}