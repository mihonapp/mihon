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
import tachiyomi.domain.translation.model.LanguageCodes
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * SYSTRAN translation engine.
 * Uses the SYSTRAN Translate API.
 * https://api-translate.systran.net
 */
class SystranTranslateEngine(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) : TranslationEngine {

    private val client: OkHttpClient get() = networkHelper.client

    override val id: Long = ENGINE_ID
    override val name: String = "SYSTRAN"
    override val requiresApiKey: Boolean = true
    override val isRateLimited: Boolean = true // 1000 characters/minute
    override val isOffline: Boolean = false

    override val supportedLanguages: List<Pair<String, String>> = listOf(
        "auto" to "Auto-detect",
        "ar" to "Arabic",
        "zh" to "Chinese",
        "cs" to "Czech",
        "da" to "Danish",
        "nl" to "Dutch",
        "en" to "English",
        "et" to "Estonian",
        "fi" to "Finnish",
        "fr" to "French",
        "de" to "German",
        "el" to "Greek",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hu" to "Hungarian",
        "id" to "Indonesian",
        "it" to "Italian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "lv" to "Latvian",
        "lt" to "Lithuanian",
        "no" to "Norwegian",
        "pl" to "Polish",
        "pt" to "Portuguese",
        "ro" to "Romanian",
        "ru" to "Russian",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "es" to "Spanish",
        "sv" to "Swedish",
        "th" to "Thai",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "vi" to "Vietnamese",
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val apiUrl = "https://api-translate.systran.net/translation/text/translate"

    @Serializable
    private data class TranslateRequest(
        val input: String,
        val source: String? = null,
        val target: String,
        val format: String = "text",
    )

    @Serializable
    private data class TranslateResponse(
        val outputs: List<Output>? = null,
        val error: ErrorInfo? = null,
    )

    @Serializable
    private data class Output(
        val output: String,
        val detectedLanguage: String? = null,
    )

    @Serializable
    private data class ErrorInfo(
        val message: String,
        val statusCode: Int? = null,
    )

    override fun isConfigured(): Boolean {
        return preferences.systranApiKey().get().isNotBlank()
    }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = preferences.systranApiKey().get()

        if (apiKey.isBlank()) {
            return@withContext TranslationResult.Error(
                "SYSTRAN API key not configured",
                TranslationResult.ErrorCode.API_KEY_MISSING,
            )
        }

        try {
            val translatedTexts = texts.map { text ->
                translateSingleText(apiKey, text, sourceLanguage, targetLanguage)
            }

            TranslationResult.Success(translatedTexts)
        } catch (e: TranslationException) {
            TranslationResult.Error(e.message ?: "Translation failed", e.errorCode)
        } catch (e: Exception) {
            TranslationResult.Error(
                e.message ?: "Unknown error",
                TranslationResult.ErrorCode.UNKNOWN,
            )
        }
    }

    private suspend fun translateSingleText(
        apiKey: String,
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        val requestBody = json.encodeToString(
            TranslateRequest.serializer(),
            TranslateRequest(
                input = text,
                source = if (sourceLanguage == "auto") null else sourceLanguage,
                target = targetLanguage,
            ),
        )

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            val errorCode = when (response.code) {
                401 -> TranslationResult.ErrorCode.API_KEY_INVALID
                429 -> TranslationResult.ErrorCode.RATE_LIMITED
                402, 403 -> TranslationResult.ErrorCode.QUOTA_EXCEEDED
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
        return translateResponse.outputs?.firstOrNull()?.output
            ?: throw TranslationException("Empty response from SYSTRAN", TranslationResult.ErrorCode.UNKNOWN)
    }

    private class TranslationException(
        message: String,
        val errorCode: TranslationResult.ErrorCode,
    ) : Exception(message)

    companion object {
        const val ENGINE_ID = 5L
    }
}
