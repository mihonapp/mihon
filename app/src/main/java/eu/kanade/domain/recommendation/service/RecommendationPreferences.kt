package eu.kanade.domain.recommendation.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class RecommendationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun networkEnabled(sourceId: Long): Preference<Boolean> {
        return preferenceStore.getBoolean("recommendation_source_${sourceId}_network_enabled_v1", false)
    }
}
