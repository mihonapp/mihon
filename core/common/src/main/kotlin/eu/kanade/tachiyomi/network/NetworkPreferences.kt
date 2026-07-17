package eu.kanade.tachiyomi.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.core.common.FeatureFlags
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class NetworkPreferences(preferenceStore: PreferenceStore) {

    val verboseLogging: Preference<Boolean> = preferenceStore.getBoolean("verbose_logging", FeatureFlags.verboseLoggingDefault)

    val dohProvider: Preference<Int> = preferenceStore.getInt("doh_provider", -1)

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
    )
}
