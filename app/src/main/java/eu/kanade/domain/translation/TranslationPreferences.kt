package eu.kanade.domain.translation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun enabled() = preferenceStore.getBoolean("pref_translation_enabled", false)

    /** Selected backend. Existing installs default to Gemini. */
    fun provider() = preferenceStore.getString("pref_translation_provider", AiProvider.GEMINI.id)

    /** Private API key storage is intentionally excluded from backups. */
    fun apiKey(provider: AiProvider = AiProvider.GEMINI) = preferenceStore.getString(
        Preference.privateKey("pref_translation_api_key_${provider.id}"),
        if (provider == AiProvider.GEMINI) preferenceStore.getString(LEGACY_KEY, "").get() else "",
    )

    /** Compatibility accessor used by older callers and migration code. */
    fun apiKey() = apiKey(AiProvider.GEMINI)

    fun customBaseUrl() = preferenceStore.getString("pref_translation_custom_base_url", "")

    /** JSON-encoded List<AiModel>; not private because it contains no credentials. */
    fun modelCatalog(
        provider: AiProvider,
    ) = preferenceStore.getString("pref_translation_model_catalog_${provider.id}", "")

    fun targetLanguage() = preferenceStore.getString(
        "pref_translation_target_lang",
        TranslationOptions.DEFAULT_LANGUAGE,
    )

    fun arabicStyle() = preferenceStore.getString(
        "pref_translation_arabic_style",
        TranslationOptions.ARABIC_STYLE_BALADI,
    )

    fun model(provider: AiProvider = AiProvider.GEMINI) = preferenceStore.getString(
        "pref_translation_model_${provider.id}",
        if (provider == AiProvider.GEMINI) {
            preferenceStore.getString("pref_translation_model", TranslationOptions.DEFAULT_MODEL).get()
        } else {
            ""
        },
    )

    fun mode() = preferenceStore.getString("pref_translation_mode", TranslationOptions.MODE_LOCAL_OCR)

    fun ocrScript() = preferenceStore.getString("pref_translation_ocr_script", TranslationOptions.OCR_SCRIPT_AUTO)

    fun textModel(provider: AiProvider = AiProvider.GEMINI) = preferenceStore.getString(
        "pref_translation_text_model_${provider.id}",
        if (provider == AiProvider.GEMINI) {
            preferenceStore.getString("pref_translation_text_model", TranslationOptions.DEFAULT_TEXT_MODEL).get()
        } else {
            ""
        },
    )

    fun translateSfx() = preferenceStore.getBoolean("pref_translation_sfx", false)

    fun glossary(mangaId: Long) = preferenceStore.getString("pref_translation_glossary_$mangaId", "")

    fun fontStyle() = preferenceStore.getString("pref_translation_font", "arabic_manga")

    fun customFontPath() = preferenceStore.getString("pref_translation_custom_font_path", "")

    fun textSizePercent() = preferenceStore.getInt("pref_translation_text_size", 100)

    fun placement() = preferenceStore.getString("pref_translation_placement", "replace")

    fun onlyWhenIdle() = preferenceStore.getBoolean("pref_translation_only_idle", false)

    fun requestDelaySeconds() = preferenceStore.getInt("pref_translation_delay_seconds", 6)

    fun saveLog() = preferenceStore.getBoolean("pref_translation_save_log", true)

    fun autoPreTranslate() = preferenceStore.getBoolean("pref_translation_auto_pre_translate", true)

    fun autoApplyTranslation() = preferenceStore.getBoolean("pref_translation_auto_apply", true)

    fun statsPagesCount() = preferenceStore.getInt("pref_translation_stats_pages", 0)

    fun statsTotalTimeMs() = preferenceStore.getLong("pref_translation_stats_time_ms", 0L)

    fun recordTranslationStats(pages: Int = 1, durationMs: Long) {
        if (pages <= 0) return
        statsPagesCount().set(statsPagesCount().get() + pages)
        statsTotalTimeMs().set(statsTotalTimeMs().get() + durationMs)
    }

    fun getEstimatedSavedDataMB(): Float {
        val pages = statsPagesCount().get()
        return pages * 1.5f
    }

    fun getAverageLatencySeconds(): Float {
        val pages = statsPagesCount().get()
        val totalMs = statsTotalTimeMs().get()
        return if (pages > 0) (totalMs / pages.toFloat()) / 1000f else 0f
    }

    private companion object {
        // Kept readable so existing users do not lose their Gemini key on upgrade.
        const val LEGACY_KEY = "pref_nano_banana_api_key"
    }
}
