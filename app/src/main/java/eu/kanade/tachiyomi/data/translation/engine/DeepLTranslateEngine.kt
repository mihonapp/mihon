package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.model.TranslationResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * DeepL translation engine.
 * Uses the DeepL API for high-quality translation.
 * https://www.deepl.com/pro-api
 */
class DeepLTranslateEngine(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) : TranslationEngine {

    private val client: OkHttpClient get() = networkHelper.client

    override val id: Long = ENGINE_ID
    override val name: String = "DeepL"
    override val requiresApiKey: Boolean = true
    override val isRateLimited: Boolean = true
    override val isOffline: Boolean = false

    override val supportedLanguages: List<Pair<String, String>> = listOf(
        "auto" to "Auto-detect",
        "BG" to "Bulgarian",
        "CS" to "Czech",
        "DA" to "Danish",
        "DE" to "German",
        "EL" to "Greek",
        "EN" to "English",
        "EN-GB" to "English (UK)",
        "EN-US" to "English (US)",
        "ES" to "Spanish",
        "ET" to "Estonian",
        "FI" to "Finnish",
        "FR" to "French",
        "HU" to "Hungarian",
        "ID" to "Indonesian",
        "IT" to "Italian",
        "JA" to "Japanese",
        "KO" to "Korean",
        "LT" to "Lithuanian",
        "LV" to "Latvian",
        "NB" to "Norwegian",
        "NL" to "Dutch",
        "PL" to "Polish",
        "PT" to "Portuguese",
        "PT-BR" to "Portuguese (Brazil)",
        "PT-PT" to "Portuguese (Portugal)",
        "RO" to "Romanian",
        "RU" to "Russian",
        "SK" to "Slovak",
        "SL" to "Slovenian",
        "SV" to "Swedish",
        "TR" to "Turkish",
        "UK" to "Ukrainian",
        "ZH" to "Chinese",
    )

    private val json = Json { ignoreUnknownKeys = true }

    // DeepL uses different API endpoints for free vs pro accounts
    private fun getApiUrl(): String {
        val apiKey = preferences.deepLApiKey().get()
        // Free API keys end with ":fx"
        return if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }
    }

    @Serializable
    private data class TranslateResponse(
        val translations: List<Translation>? = null,
        val message: String? = null, // Error message
    )

    @Serializable
    private data class Translation(
        @SerialName("detected_source_language")
        val detectedSourceLanguage: String? = null,
        val text: String,
    )

    override fun isConfigured(): Boolean {
        return preferences.deepLApiKey().get().isNotBlank()
    }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = preferences.deepLApiKey().get()

        if (apiKey.isBlank()) {
            return@withContext TranslationResult.Error(
                "DeepL API key not configured",
                TranslationResult.ErrorCode.API_KEY_MISSING,
            )
        }

        try {
            // DeepL supports batch translation
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
        val formBodyBuilder = FormBody.Builder()

        texts.forEach { text ->
            formBodyBuilder.add("text", text)
        }

        // DeepL uses uppercase language codes
        val targetLang = targetLanguage.uppercase()
        formBodyBuilder.add("target_lang", targetLang)

        if (sourceLanguage != "auto") {
            formBodyBuilder.add("source_lang", sourceLanguage.uppercase())
        }

        // Optional: preserve formatting
        formBodyBuilder.add("preserve_formatting", "1")

        val request = Request.Builder()
            .url(getApiUrl())
            .post(formBodyBuilder.build())
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            val errorCode = when (response.code) {
                401, 403 -> TranslationResult.ErrorCode.API_KEY_INVALID
                429 -> TranslationResult.ErrorCode.RATE_LIMITED
                456 -> TranslationResult.ErrorCode.QUOTA_EXCEEDED
                503 -> TranslationResult.ErrorCode.SERVICE_UNAVAILABLE
                else -> TranslationResult.ErrorCode.UNKNOWN
            }

            val errorMessage = try {
                val errorResponse = json.decodeFromString(TranslateResponse.serializer(), responseBody)
                errorResponse.message ?: "HTTP ${response.code}"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBody"
            }

            throw TranslationException(errorMessage, errorCode)
        }

        val translateResponse = json.decodeFromString(TranslateResponse.serializer(), responseBody)
        val translations = translateResponse.translations
            ?: throw TranslationException("Empty response from DeepL", TranslationResult.ErrorCode.UNKNOWN)

        return BatchResult(
            translatedTexts = translations.map { it.text },
            detectedLanguage = translations.firstOrNull()?.detectedSourceLanguage?.lowercase(),
        )
    }

    private class TranslationException(
        message: String,
        val errorCode: TranslationResult.ErrorCode,
    ) : Exception(message)

    companion object {
        const val ENGINE_ID = 6L
    }
}
