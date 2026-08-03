package eu.kanade.tachiyomi.core.security

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class PrivacyPreferences(
    preferenceStore: PreferenceStore,
) {
    val crashlytics: Preference<Boolean> = preferenceStore.getBoolean("crashlytics", false)

    val analytics: Preference<Boolean> = preferenceStore.getBoolean("analytics", false)
}
