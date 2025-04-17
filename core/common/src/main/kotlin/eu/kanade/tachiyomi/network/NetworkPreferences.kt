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
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0",
        )
    }
}
