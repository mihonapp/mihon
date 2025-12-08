package eu.kanade.tachiyomi.network

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NetworkPreferences(
    private val preferenceStore: PreferenceStore,
    private val verboseLogging: Boolean = false,
) {

    fun verboseLogging(): Preference<Boolean> {
        return preferenceStore.getBoolean("verbose_logging", verboseLogging)
    }

    fun dohProvider(): Preference<Int> {
        return preferenceStore.getInt("doh_provider", -1)
    }

    fun defaultUserAgent(): Preference<String> {
        return preferenceStore.getString(
            "default_user_agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
        )
    }
}
