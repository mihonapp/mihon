package eu.kanade.tachiyomi.core.security

import tachiyomi.core.common.preference.PreferenceStore

class PrivacyPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun crashlytics() = preferenceStore.getBoolean("crashlytics", false)

    fun analytics() = preferenceStore.getBoolean("analytics", false)
}
