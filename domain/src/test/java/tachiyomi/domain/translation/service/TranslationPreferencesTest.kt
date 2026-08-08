package tachiyomi.domain.translation.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference

class TranslationPreferencesTest {

    @Test
    fun `default model and private api key are configured`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())

        preferences.geminiModel.get() shouldBe "gemini-3.6-flash"
        Preference.isPrivate(preferences.geminiApiKey.key()) shouldBe true
        Preference.isPrivate(preferences.setupFingerprint.key()) shouldBe true
        preferences.maxOutputTokens.get() shouldBe 65_536
        preferences.sourceLanguage.get() shouldBe "auto"
        preferences.overlayTextSizeMode.get() shouldBe "dynamic"
        preferences.overlayTextSizeSp.get() shouldBe 16
        preferences.globalInstructions.get() shouldBe DEFAULT_TRANSLATION_SYSTEM_PROMPT
        preferences.overlayFontFamily.get() shouldBe "system"
        preferences.overlayTextColor.get() shouldBe "#FF000000"
        preferences.overlayBoxFillColor.get() shouldBe "#D2FFFFFF"
        preferences.overlayBoxStrokeColor.get() shouldBe "#E6202020"
        preferences.overlayBoxPaddingDp.get() shouldBe 0
        preferences.overlayTextAlignment.get() shouldBe "center"
        preferences.queueSwipeStartAction.get() shouldBe TranslationQueueSwipeAction.ViewLogs
        preferences.queueSwipeEndAction.get() shouldBe TranslationQueueSwipeAction.CancelOrDelete
        preferences.logSwipeStartAction.get() shouldBe TranslationLogSwipeAction.OpenDetails
        preferences.logSwipeEndAction.get() shouldBe TranslationLogSwipeAction.CopyDetails
    }
}
