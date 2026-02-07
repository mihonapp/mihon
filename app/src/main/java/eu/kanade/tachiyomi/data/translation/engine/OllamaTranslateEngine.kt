package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit

/**
 * Ollama local LLM translation engine.
 * Uses locally-hosted Ollama for offline AI translation.
 * https://github.com/ollama/ollama
 */
class OllamaTranslateEngine(
    private val preferences: TranslationPreferences = Injekt.get(),
) : TranslationEngine {

    override val id: Long = ENGINE_ID
    override val name: String = "Ollama (Local AI)"
    override val requiresApiKey: Boolean = false
    override val isRateLimited: Boolean = false // Local, no rate limits
    override val isOffline: Boolean = true

    override val supportedLanguages: List<Pair<String, String>> = LanguageCodes.COMMON_LANGUAGES

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Create a client with longer timeout for local LLM
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // Local LLMs can be slow
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Serializable
    private data class GenerateRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean = false,
    )

    @Serializable
    private data class GenerateResponse(
        val response: String? = null,
        val done: Boolean? = null,
        val created_at: String? = null,
        val error: String? = null,
    )

    override fun isConfigured(): Boolean {
        // Check if Ollama is reachable
        return preferences.ollamaUrl().get().isNotBlank() &&
            preferences.ollamaModel().get().isNotBlank()
    }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult = withContext(Dispatchers.IO) {
        val ollamaUrl = preferences.ollamaUrl().get().trimEnd('/')
        val model = preferences.ollamaModel().get()

        if (ollamaUrl.isBlank()) {
            return@withContext TranslationResult.Error(
                "Ollama server URL not configured",
                TranslationResult.ErrorCode.API_KEY_MISSING,
            )
        }

        if (model.isBlank()) {
            return@withContext TranslationResult.Error(
                "Ollama model not configured",
                TranslationResult.ErrorCode.API_KEY_MISSING,
            )
        }

        try {
            val translatedTexts = texts.map { text ->
                translateSingleText(ollamaUrl, model, text, sourceLanguage, targetLanguage)
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
        ollamaUrl: String,
        model: String,
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        val sourceLangName = LanguageCodes.getDisplayName(sourceLanguage)
        val targetLangName = LanguageCodes.getDisplayName(targetLanguage)

        // Use custom prompt if configured
        val customPrompt = preferences.ollamaPrompt().get()
        val prompt = if (customPrompt.isNotBlank()) {
            customPrompt
                .replace("{SOURCE_LANG}", sourceLangName)
                .replace("{TARGET_LANG}", targetLangName)
                .replace("{TEXT}", text)
        } else {
            """You are a professional translator specializing in novel/fiction translation.
Translate the following text from $sourceLangName to $targetLangName.
Rules:
- Only output the translation, nothing else
- Preserve paragraph structure (keep empty lines between paragraphs)
- Maintain the author's writing style and tone
- Keep character names consistent
- Do not add explanations or notes

Text to translate:
$text

Translation:"""
        }

        val request = GenerateRequest(
            model = model,
            prompt = prompt,
            stream = false,
        )

        val requestBody = json.encodeToString(GenerateRequest.serializer(), request)
        val apiUrl = "$ollamaUrl/api/generate"

        val httpRequest = Request.Builder()
            .url(apiUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            val errorCode = when (response.code) {
                404 -> TranslationResult.ErrorCode.SERVICE_UNAVAILABLE
                else -> TranslationResult.ErrorCode.UNKNOWN
            }
            throw TranslationException("Ollama error: HTTP ${response.code}", errorCode)
        }

        // Ollama sometimes returns multiple JSON objects even with stream:false
        // Parse them and combine the responses
        val fullResponse = parseOllamaResponse(responseBody)

        return fullResponse.trim().ifEmpty {
            throw TranslationException("Empty response from Ollama", TranslationResult.ErrorCode.UNKNOWN)
        }
    }

    private fun parseOllamaResponse(responseBody: String): String {
        val responses = mutableListOf<GenerateResponse>()
        
        // Try parsing as single JSON object first
        try {
            val singleResponse = json.decodeFromString(GenerateResponse.serializer(), responseBody)
            if (singleResponse.error != null) {
                throw TranslationException(
                    "Ollama error: ${singleResponse.error}",
                    TranslationResult.ErrorCode.UNKNOWN,
                )
            }
            return singleResponse.response ?: ""
        } catch (e: Exception) {
            // If single parse fails, try parsing multiple newline-delimited JSON objects
        }

        // Parse multiple JSON objects separated by newlines
        responseBody.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val partialResponse = json.decodeFromString(GenerateResponse.serializer(), line.trim())
                if (partialResponse.error != null) {
                    throw TranslationException(
                        "Ollama error: ${partialResponse.error}",
                        TranslationResult.ErrorCode.UNKNOWN,
                    )
                }
                responses.add(partialResponse)
            } catch (e: Exception) {
                // Skip invalid JSON lines
            }
        }

        // Combine all response parts
        return responses.mapNotNull { it.response }.joinToString("")
    }

    private class TranslationException(
        message: String,
        val errorCode: TranslationResult.ErrorCode,
    ) : Exception(message)

    companion object {
        const val ENGINE_ID = 4L
    }
}
