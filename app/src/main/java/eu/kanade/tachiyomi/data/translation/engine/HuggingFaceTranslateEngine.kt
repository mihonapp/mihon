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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Free translation engine using Hugging Face's Helsinki-NLP models.
 * No API key required, but has limited language pairs.
 */
class HuggingFaceTranslateEngine(
    private val networkHelper: NetworkHelper = Injekt.get(),
) : TranslationEngine {

    private val client: OkHttpClient get() = networkHelper.client

    override val id: Long = ENGINE_ID
    override val name: String = "HuggingFace (Free)"
    override val requiresApiKey: Boolean = false
    override val isRateLimited: Boolean = true
    override val isOffline: Boolean = false

    // Helsinki-NLP models support these language pairs
    override val supportedLanguages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "zh" to "Chinese",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ja" to "Japanese",
        "ko" to "Korean",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "it" to "Italian",
        "nl" to "Dutch",
        "pl" to "Polish",
        "ar" to "Arabic",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "id" to "Indonesian",
        "fi" to "Finnish",
        "sv" to "Swedish",
        "da" to "Danish",
        "no" to "Norwegian",
        "uk" to "Ukrainian",
        "cs" to "Czech",
        "ro" to "Romanian",
        "hu" to "Hungarian",
        "el" to "Greek",
        "he" to "Hebrew",
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Base URL for Helsinki-NLP models on Hugging Face
    private val baseUrl = "https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt"

    @Serializable
    private data class TranslationRequest(
        val inputs: String,
    )

    @Serializable
    private data class TranslationResponse(
        @SerialName("translation_text")
        val translationText: String? = null,
    )

    @Serializable
    private data class ErrorResponse(
        val error: String? = null,
    )

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult = withContext(Dispatchers.IO) {
        // Same language, no translation needed
        if (sourceLanguage == targetLanguage) {
            return@withContext TranslationResult.Success(texts)
        }

        try {
            val translatedTexts = texts.map { text ->
                if (text.isBlank()) {
                    text
                } else {
                    translateSingleText(text, sourceLanguage, targetLanguage)
                }
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
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        // Build the model URL for the language pair
        val modelUrl = "$baseUrl-$sourceLanguage-$targetLanguage"

        val requestBody = json.encodeToString(
            TranslationRequest.serializer(),
            TranslationRequest(inputs = text),
        )

        val httpRequest = Request.Builder()
            .url(modelUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            val errorCode = when (response.code) {
                429 -> TranslationResult.ErrorCode.RATE_LIMITED
                503 -> TranslationResult.ErrorCode.SERVICE_UNAVAILABLE
                404 -> TranslationResult.ErrorCode.LANGUAGE_NOT_SUPPORTED
                else -> TranslationResult.ErrorCode.UNKNOWN
            }

            val errorMessage = try {
                json.decodeFromString(ErrorResponse.serializer(), responseBody).error
                    ?: "HTTP ${response.code}"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBody"
            }

            throw TranslationException(errorMessage, errorCode)
        }

        // Response is an array
        val translations = try {
            json.decodeFromString<List<TranslationResponse>>(responseBody)
        } catch (e: Exception) {
            // Try single object
            listOf(json.decodeFromString<TranslationResponse>(responseBody))
        }

        return translations.firstOrNull()?.translationText
            ?: throw TranslationException(
                "Empty response from HuggingFace",
                TranslationResult.ErrorCode.UNKNOWN,
            )
    }

    private class TranslationException(
        message: String,
        val errorCode: TranslationResult.ErrorCode,
    ) : Exception(message)

    companion object {
        const val ENGINE_ID = 5L
    }
}
