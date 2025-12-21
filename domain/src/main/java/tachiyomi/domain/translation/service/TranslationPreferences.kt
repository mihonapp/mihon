package tachiyomi.domain.translation.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preferences for translation services.
 */
class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * Whether translation is enabled.
     */
    fun translationEnabled() = preferenceStore.getBoolean(
        "translation_enabled",
        false,
    )

    /**
     * Selected translation engine ID.
     */
    fun selectedEngineId() = preferenceStore.getLong(
        "translation_engine_id",
        0L, // Default to first engine (usually Google ML Kit)
    )

    /**
     * Source language code for translation.
     */
    fun sourceLanguage() = preferenceStore.getString(
        "translation_source_language",
        "auto",
    )

    /**
     * Target language code for translation.
     */
    fun targetLanguage() = preferenceStore.getString(
        "translation_target_language",
        "en",
    )

    /**
     * Delay between translation requests in milliseconds (for rate-limited engines).
     */
    fun rateLimitDelayMs() = preferenceStore.getInt(
        "translation_rate_limit_delay",
        3000, // 3 seconds default
    )

    /**
     * Whether to auto-download chapters before translating.
     */
    fun autoDownloadBeforeTranslate() = preferenceStore.getBoolean(
        "translation_auto_download",
        true,
    )

    /**
     * Whether to auto-translate downloaded chapters.
     */
    fun autoTranslateDownloads() = preferenceStore.getBoolean(
        "translation_auto_translate_downloads",
        false,
    )

    /**
     * Chapter count threshold to show rate limit warning.
     */
    fun rateLimitWarningThreshold() = preferenceStore.getInt(
        "translation_rate_limit_warning_threshold",
        10,
    )

    /**
     * Whether to bypass the rate limit warning.
     */
    fun bypassRateLimitWarning() = preferenceStore.getBoolean(
        "translation_bypass_rate_limit_warning",
        false,
    )

    /**
     * Maximum parallel translation tasks for offline engines.
     */
    fun maxParallelTranslations() = preferenceStore.getInt(
        "translation_max_parallel",
        3,
    )

    // API Keys for various services

    /**
     * OpenAI API key.
     */
    fun openAiApiKey() = preferenceStore.getString(
        "translation_openai_api_key",
        "",
    )

    /**
     * DeepSeek API key.
     */
    fun deepSeekApiKey() = preferenceStore.getString(
        "translation_deepseek_api_key",
        "",
    )

    /**
     * LibreTranslate server URL.
     */
    fun libreTranslateUrl() = preferenceStore.getString(
        "translation_libretranslate_url",
        "https://libretranslate.com/translate",
    )

    /**
     * LibreTranslate API key (optional).
     */
    fun libreTranslateApiKey() = preferenceStore.getString(
        "translation_libretranslate_api_key",
        "",
    )

    /**
     * SYSTRAN API key.
     */
    fun systranApiKey() = preferenceStore.getString(
        "translation_systran_api_key",
        "",
    )

    /**
     * DeepL API key.
     */
    fun deepLApiKey() = preferenceStore.getString(
        "translation_deepl_api_key",
        "",
    )

    /**
     * Google Cloud Translation API key.
     */
    fun googleApiKey() = preferenceStore.getString(
        "translation_google_api_key",
        "",
    )

    /**
     * Ollama server URL for local AI translation.
     */
    fun ollamaUrl() = preferenceStore.getString(
        "translation_ollama_url",
        "http://localhost:11434",
    )

    /**
     * Ollama model name.
     */
    fun ollamaModel() = preferenceStore.getString(
        "translation_ollama_model",
        "llama3",
    )

    /**
     * Whether to enable real-time translation while reading.
     */
    fun realTimeTranslation() = preferenceStore.getBoolean(
        "translation_realtime",
        false,
    )

    /**
     * Cache translated content for faster re-reading.
     */
    fun cacheTranslations() = preferenceStore.getBoolean(
        "translation_cache_enabled",
        true,
    )

    /**
     * Translation request timeout in milliseconds.
     * Default is 2 minutes (120000ms).
     */
    fun translationTimeoutMs() = preferenceStore.getLong(
        "translation_timeout_ms",
        120000L, // 2 minutes default
    )
}
