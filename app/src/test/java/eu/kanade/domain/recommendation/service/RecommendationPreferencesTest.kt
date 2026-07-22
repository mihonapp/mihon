package eu.kanade.domain.recommendation.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class RecommendationPreferencesTest {

    private val preferences = RecommendationPreferences(InMemoryPreferenceStore())

    @Test
    fun `network access is disabled by default`() {
        assertFalse(preferences.networkEnabled(1L).get())
    }

    @Test
    fun `network access is scoped by source id`() {
        val firstSource = preferences.networkEnabled(1L)
        val secondSource = preferences.networkEnabled(2L)

        firstSource.set(true)

        assertTrue(firstSource.get())
        assertFalse(secondSource.get())
        assertNotEquals(firstSource.key(), secondSource.key())
    }
}
