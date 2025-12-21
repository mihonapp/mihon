package tachiyomi.domain.translation.model

/**
 * Base interface for translation engines.
 */
interface TranslationEngine {
    /**
     * Unique identifier for this engine.
     */
    val id: Long

    /**
     * Display name of the engine.
     */
    val name: String

    /**
     * Whether this engine requires an API key.
     */
    val requiresApiKey: Boolean

    /**
     * Whether this engine is rate-limited (web-based).
     * Offline engines like ML Kit don't need rate limiting.
     */
    val isRateLimited: Boolean

    /**
     * Whether this engine works offline.
     */
    val isOffline: Boolean

    /**
     * List of supported languages as (code, displayName) pairs.
     */
    val supportedLanguages: List<Pair<String, String>>

    /**
     * Translate a list of text segments.
     *
     * @param texts List of text segments to translate
     * @param sourceLanguage Source language code (e.g., "en", "auto")
     * @param targetLanguage Target language code (e.g., "zh", "ja")
     * @return Result containing translated texts or error
     */
    suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult

    /**
     * Translate a single text.
     */
    suspend fun translateSingle(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult {
        return translate(listOf(text), sourceLanguage, targetLanguage)
    }

    /**
     * Check if the engine is properly configured (API key set, etc.).
     */
    fun isConfigured(): Boolean = true
}

/**
 * Result of a translation operation.
 */
sealed class TranslationResult {
    /**
     * Successful translation.
     */
    data class Success(
        val translatedTexts: List<String>,
        val detectedSourceLanguage: String? = null,
    ) : TranslationResult()

    /**
     * Translation failed.
     */
    data class Error(
        val message: String,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN,
    ) : TranslationResult()

    enum class ErrorCode {
        UNKNOWN,
        NETWORK_ERROR,
        API_KEY_INVALID,
        API_KEY_MISSING,
        RATE_LIMITED,
        QUOTA_EXCEEDED,
        LANGUAGE_NOT_SUPPORTED,
        TEXT_TOO_LONG,
        SERVICE_UNAVAILABLE,
    }
}

/**
 * Common language codes used across translation engines.
 */
object LanguageCodes {
    val COMMON_LANGUAGES = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "zh" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "th" to "Thai",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "ms" to "Malay",
        "tl" to "Filipino",
        "tr" to "Turkish",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "da" to "Danish",
        "fi" to "Finnish",
        "no" to "Norwegian",
        "uk" to "Ukrainian",
        "cs" to "Czech",
        "ro" to "Romanian",
        "hu" to "Hungarian",
        "el" to "Greek",
        "he" to "Hebrew",
        "fa" to "Persian",
        "bn" to "Bengali",
    )

    fun getDisplayName(code: String): String {
        return COMMON_LANGUAGES.find { it.first == code }?.second ?: code
    }
}
