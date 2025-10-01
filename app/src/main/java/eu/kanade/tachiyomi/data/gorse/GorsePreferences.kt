package eu.kanade.tachiyomi.data.gorse

import tachiyomi.core.common.preference.PreferenceStore

class GorsePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun gorseServerUrl() = preferenceStore.getString("gorse_server_url", "http://172.20.182.31:8088")
    
    fun gorseEnabled() = preferenceStore.getBoolean("gorse_enabled", true)
}
