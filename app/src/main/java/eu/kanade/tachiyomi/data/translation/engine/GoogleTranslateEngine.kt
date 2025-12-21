package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * Google Translate engine using the Cloud Translation API v2.
 * https://translation.googleapis.com/language/translate/v2
 * Rate limit: 180,000 characters/minute
 */
class GoogleTranslateEngine(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) : TranslationEngine {

    private val client: OkHttpClient get() = networkHelper.client

    override val id: Long = ENGINE_ID
    override val name: String = "Google Translate"
    override val requiresApiKey: Boolean = true
    override val isRateLimited: Boolean = true
    override val isOffline: Boolean = false

    override val supportedLanguages: List<Pair<String, String>> = listOf(
        "auto" to "Auto-detect",
        "af" to "Afrikaans",
        "ar" to "Arabic",
        "bg" to "Bulgarian",
        "bn" to "Bengali",
        "ca" to "Catalan",
        "cs" to "Czech",
        "da" to "Danish",
        "de" to "German",
        "el" to "Greek",
        "en" to "English",
        "es" to "Spanish",
        "et" to "Estonian",
        "fa" to "Persian",
        "fi" to "Finnish",
        "fr" to "French",
        "gu" to "Gujarati",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hr" to "Croatian",
        "hu" to "Hungarian",
        "id" to "Indonesian",
        "it" to "Italian",
        "ja" to "Japanese",
        "kn" to "Kannada",
        "ko" to "Korean",
        "lt" to "Lithuanian",
        "lv" to "Latvian",
        "ml" to "Malayalam",
        "mr" to "Marathi",
        "ms" to "Malay",
        "nl" to "Dutch",
        "no" to "Norwegian",
        "pa" to "Punjabi",
        "pl" to "Polish",
        "pt" to "Portuguese",
        "ro" to "Romanian",
        "ru" to "Russian",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "sr" to "Serbian",
        "sv" to "Swedish",
        "sw" to "Swahili",
        "ta" to "Tamil",
        "te" to "Telugu",
        "th" to "Thai",
        "tl" to "Filipino",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "ur" to "Urdu",
        "vi" to "Vietnamese",
        "zh" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = "https://translation.googleapis.com/language/translate/v2"

    @Serializable
    private data class TranslateRequest(
        val q: List<String>,
        val target: String,
        val source: String? = null,
        val format: String = "text",
    )

    @Serializable
    private data class TranslateResponse(
        val data: TranslationData? = null,
        val error: ErrorInfo? = null,
    )

    @Serializable
    private data class TranslationData(
        val translations: List<TranslationItem>,
    )

    @Serializable
    private data class TranslationItem(
        val translatedText: String,
        val detectedSourceLanguage: String? = null,
    )

    @Serializable
    private data class ErrorInfo(
        val code: Int,
        val message: String,
        val status: String? = null,
    )

    override fun isConfigured(): Boolean {
        return preferences.googleApiKey().get().isNotBlank()
    }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = preferences.googleApiKey().get()

        if (apiKey.isBlank()) {
            return@withContext TranslationResult.Error(
                "Google Translate API key not configured",
                TranslationResult.ErrorCode.API_KEY_MISSING,
            )
        }

        try {
            val result = translateBatch(apiKey, texts, sourceLanguage, targetLanguage)

            TranslationResult.Success(
                result.translatedTexts,
                result.detectedLanguage,
            )
        } catch (e: TranslationException) {
            TranslationResult.Error(e.message ?: "Translation failed", e.errorCode)
        } catch (e: Exception) {
            TranslationResult.Error(
                e.message ?: "Unknown error",
                TranslationResult.ErrorCode.UNKNOWN,
            )
        }
    }

    private data class BatchResult(
        val translatedTexts: List<String>,
        val detectedLanguage: String?,
    )

    private suspend fun translateBatch(
        apiKey: String,
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): BatchResult {
        val requestBody = json.encodeToString(
            TranslateRequest.serializer(),
            TranslateRequest(
                q = texts,
                target = targetLanguage,
                source = if (sourceLanguage == "auto") null else sourceLanguage,
            ),
        )

        val request = Request.Builder()
            .url("$baseUrl?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            val errorCode = when (response.code) {
                401, 403 -> TranslationResult.ErrorCode.API_KEY_INVALID
                429 -> TranslationResult.ErrorCode.RATE_LIMITED
                402 -> TranslationResult.ErrorCode.QUOTA_EXCEEDED
                503 -> TranslationResult.ErrorCode.SERVICE_UNAVAILABLE
                else -> TranslationResult.ErrorCode.UNKNOWN
            }

            val errorMessage = try {
                val errorResponse = json.decodeFromString(TranslateResponse.serializer(), responseBody)
                errorResponse.error?.message ?: "HTTP ${response.code}"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBody"
            }

            throw TranslationException(errorMessage, errorCode)
        }

        val translateResponse = json.decodeFromString(TranslateResponse.serializer(), responseBody)
        val translations = translateResponse.data?.translations
            ?: throw TranslationException("Empty response from Google", TranslationResult.ErrorCode.UNKNOWN)

        return BatchResult(
            translatedTexts = translations.map { it.translatedText },
            detectedLanguage = translations.firstOrNull()?.detectedSourceLanguage,
        )
    }

    private class TranslationException(
        message: String,
        val errorCode: TranslationResult.ErrorCode,
    ) : Exception(message)

    companion object {
        const val ENGINE_ID = 7L
    }
}
