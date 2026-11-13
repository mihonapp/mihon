package eu.kanade.tachiyomi.ui.reader.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.translation.ocr.OcrPresetCatalog
import tachiyomi.translation.provider.ProviderConfig
import tachiyomi.translation.provider.ProviderRegistry

enum class TranslationSourceLanguage(val code: String, val label: String) {
    JAPANESE("ja", "日文"),
    KOREAN("ko", "韩文"),
    ENGLISH("en", "英文"),
}

/**
 * All translation settings live here and are edited only in Settings -> Translation.
 * The reader reads them but never writes them.
 */
@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    preferenceStore: PreferenceStore,
) {
    val enabled: Preference<Boolean> = preferenceStore.getBoolean("pref_translation_enabled", true)

    val sourceLanguage: Preference<TranslationSourceLanguage> = preferenceStore.getEnum(
        "pref_translation_source_language",
        TranslationSourceLanguage.JAPANESE,
    )

    /** Currently fixed to Simplified Chinese; stored so a future picker does not need a migration. */
    val targetLanguage: Preference<String> = preferenceStore.getString("pref_translation_target_language", "zh-CN")

    /** "paddle" (ONNX PP-OCR) or "mangaocr" (DeiT + char-BERT, Japanese only). */
    val ocrEngine: Preference<String> = preferenceStore.getString("pref_translation_ocr_engine", ENGINE_PADDLE)

    val detPresetId: Preference<String> = preferenceStore.getString(
        "pref_translation_det_preset",
        OcrPresetCatalog.defaultDet.id,
    )

    val ocrPresetId: Preference<String> = preferenceStore.getString(
        "pref_translation_ocr_preset",
        OcrPresetCatalog.default.id,
    )

    val providerId: Preference<String> = preferenceStore.getString(
        "pref_translation_provider",
        ProviderRegistry.EDGE,
    )

    val azureKey: Preference<String> = preferenceStore.getString("pref_translation_azure_key")
    val azureRegion: Preference<String> = preferenceStore.getString("pref_translation_azure_region")
    val googleCloudKey: Preference<String> = preferenceStore.getString("pref_translation_google_key")
    val llmKey: Preference<String> = preferenceStore.getString("pref_translation_llm_key")
    val llmBaseUrl: Preference<String> = preferenceStore.getString("pref_translation_llm_base_url")
    val llmModel: Preference<String> = preferenceStore.getString("pref_translation_llm_model")

    /** Image never leaves the device; text does, so this is a separate explicit consent. */
    val networkConsent: Preference<Boolean> = preferenceStore.getBoolean("pref_translation_network_consent", false)

    val useContext: Preference<Boolean> = preferenceStore.getBoolean("pref_translation_use_context", true)

    val panelHeightFraction: Preference<Float> = preferenceStore.getFloat(
        "pref_translation_panel_height",
        DEFAULT_PANEL_FRACTION,
    )

    val panelWidthFraction: Preference<Float> = preferenceStore.getFloat(
        "pref_translation_panel_width",
        DEFAULT_WIDTH_FRACTION,
    )

    fun providerConfig(): ProviderConfig = when (providerId.get()) {
        ProviderRegistry.AZURE -> ProviderConfig(
            apiKey = azureKey.get(),
            region = azureRegion.get(),
        )
        ProviderRegistry.GOOGLE_CLOUD -> ProviderConfig(apiKey = googleCloudKey.get())
        ProviderRegistry.LLM -> ProviderConfig(
            apiKey = llmKey.get(),
            model = llmModel.get(),
            baseUrl = llmBaseUrl.get(),
        )
        else -> ProviderConfig()
    }

    /** Provider credentials are complete enough to send a request. */
    fun isProviderConfigured(): Boolean {
        val descriptor = ProviderRegistry.descriptor(providerId.get()) ?: return false
        if (!descriptor.requiresKey) return true
        return when (providerId.get()) {
            ProviderRegistry.AZURE -> azureKey.get().isNotBlank() && azureRegion.get().isNotBlank()
            ProviderRegistry.LLM -> llmKey.get().isNotBlank()
            else -> providerConfig().apiKey?.isNotBlank() == true
        }
    }

    companion object {
        const val DEFAULT_PANEL_FRACTION = 0.30f
        const val MIN_PANEL_FRACTION = 0.25f
        const val MAX_PANEL_FRACTION = 0.45f
        const val ENGINE_PADDLE = "paddle"
        const val ENGINE_MANGA_OCR = "mangaocr"
        const val DEFAULT_WIDTH_FRACTION = 0.30f
        const val MIN_WIDTH_FRACTION = 0.22f
        const val MAX_WIDTH_FRACTION = 0.55f
    }
}
