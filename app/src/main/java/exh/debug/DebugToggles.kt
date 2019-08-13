package exh.debug

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

enum class DebugToggles(val default: Boolean) {
    // Redirect to master version of gallery when encountering a gallery that has a parent/child that is already in the library
    ENABLE_EXH_ROOT_REDIRECT(true),
    // Enable debug overlay (only available in debug builds)
    ENABLE_DEBUG_OVERLAY(true),
    // Convert non-root galleries into root galleries when loading them
    PULL_TO_ROOT_WHEN_LOADING_EXH_MANGA_DETAILS(true),
    // Do not update the same gallery too often
    RESTRICT_EXH_GALLERY_UPDATE_CHECK_FREQUENCY(true),
    // Pretend that all galleries only have a single version
    INCLUDE_ONLY_ROOT_WHEN_LOADING_EXH_VERSIONS(false);

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