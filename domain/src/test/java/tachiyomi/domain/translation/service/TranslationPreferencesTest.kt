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
        Preference.isPrivate(preferences.setupFingerprint.key()) shouldBe true
        preferences.temperature.get() shouldBe 1f
        preferences.topP.get() shouldBe 0.95f
        preferences.topK.get() shouldBe 64
        preferences.maxOutputTokens.get() shouldBe 65_536
        preferences.thinkingLevel.get() shouldBe "high"
        preferences.maxImagesPerBatch.get() shouldBe 38
        preferences.parallelRetryLanes.get() shouldBe "1"
        preferences.sourceLanguage.get() shouldBe "auto"
        preferences.overlayTextSizeMode.get() shouldBe "dynamic"
        preferences.overlayTextSizeSp.get() shouldBe 16
        preferences.globalInstructions.get() shouldBe DEFAULT_TRANSLATION_SYSTEM_PROMPT
        preferences.overlayFontFamily.get() shouldBe "sans"
        preferences.overlayTextColor.get() shouldBe "#FF000000"
        preferences.overlayBoxFillColor.get() shouldBe "#D2FFFFFF"
        preferences.overlayBoxStrokeColor.get() shouldBe "#E6202020"
        preferences.overlayBoxPaddingDp.get() shouldBe 4
        preferences.overlayTextAlignment.get() shouldBe "center"
        preferences.queueSwipeStartAction.get() shouldBe TranslationQueueSwipeAction.ViewLogs
        preferences.queueSwipeEndAction.get() shouldBe TranslationQueueSwipeAction.CancelOrDelete
        preferences.logSwipeStartAction.get() shouldBe TranslationLogSwipeAction.OpenDetails
        preferences.logSwipeEndAction.get() shouldBe TranslationLogSwipeAction.CopyDetails
    }
}
