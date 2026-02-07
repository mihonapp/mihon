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
     * Custom prompt template for Ollama.
     * Supports {SOURCE_LANG}, {TARGET_LANG}, and {TEXT} placeholders.
     */
    fun ollamaPrompt() = preferenceStore.getString(
        "translation_ollama_prompt",
        "",
    )

    /**
     * Custom system prompt for OpenAI/GPT models.
     */
    fun openAiSystemPrompt() = preferenceStore.getString(
        "translation_openai_system_prompt",
        "",
    )

    /**
     * Custom user prompt template for OpenAI/GPT models.
     * Supports {SOURCE_LANG}, {TARGET_LANG}, and {TEXT} placeholders.
     */
    fun openAiUserPrompt() = preferenceStore.getString(
        "translation_openai_user_prompt",
        "",
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

    /**
     * Whether to replace the manga title with the translated title.
     * When enabled, the original title is saved to alternative_titles.
     */
    fun replaceTitle() = preferenceStore.getBoolean(
        "translation_replace_title",
        false,
    )

    /**
     * Whether to translate tags and merge with original tags.
     */
    fun translateTags() = preferenceStore.getBoolean(
        "translation_translate_tags",
        false,
    )

    /**
     * Whether to replace original tags with translated tags instead of merging.
     * When false, translated tags are added to original tags.
     * When true, translated tags replace original tags.
     */
    fun replaceTagsInsteadOfMerge() = preferenceStore.getBoolean(
        "translation_replace_tags",
        false,
    )

    /**
     * Whether to save translated titles to alternative_titles.
     * Useful for keeping track of both original and translated titles.
     */
    fun saveTranslatedTitleAsAlternative() = preferenceStore.getBoolean(
        "translation_save_title_as_alternative",
        true,
    )

    // Custom HTTP Translation Engine Settings

    /**
     * Custom HTTP translation API URL.
     * Should accept POST requests with JSON body.
     */
    fun customHttpUrl() = preferenceStore.getString(
        "translation_custom_http_url",
        "",
    )

    /**
     * Custom HTTP API key (sent in Authorization header).
     */
    fun customHttpApiKey() = preferenceStore.getString(
        "translation_custom_http_api_key",
        "",
    )

    /**
     * Custom HTTP request template.
     * Use placeholders: {text}, {texts}, {source}, {target}
     * Example: {"q": "{text}", "source": "{source}", "target": "{target}"}
     */
    fun customHttpRequestTemplate() = preferenceStore.getString(
        "translation_custom_http_request_template",
        """{"q": {texts}, "source": "{source}", "target": "{target}"}""",
    )

    /**
     * Custom HTTP response JSON path for extracting translated text.
     * Use dot notation: translatedText or result.translations[0].text
     */
    fun customHttpResponsePath() = preferenceStore.getString(
        "translation_custom_http_response_path",
        "translatedText",
    )

    /**
     * Translation chunk mode: "paragraphs", "characters", or "words"
     */
    fun translationChunkMode() = preferenceStore.getString(
        "translation_chunk_mode",
        "paragraphs",
    )

    /**
     * Maximum chunk size per translation batch.
     * Meaning depends on chunkMode: paragraphs count, character count, or word count.
     */
    fun translationChunkSize() = preferenceStore.getInt(
        "translation_chunk_size",
        50,
    )
}
