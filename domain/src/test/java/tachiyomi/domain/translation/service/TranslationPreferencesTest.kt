package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference

class TranslationPreferencesTest {

    @Test
    fun `default model and private api key are configured`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())

        preferences.geminiModel.get() shouldBe "gemini-3-flash-preview"
        Preference.isPrivate(preferences.geminiApiKey.key()) shouldBe true
    }
}
