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
 * OpenAI GPT-based translation engine.
 * Uses the OpenAI API for high-quality AI translation.
 */
class OpenAITranslateEngine(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
) : TranslationEngine {

    private val client: OkHttpClient get() = networkHelper.client

    override val id: Long = ENGINE_ID
    override val name: String = "OpenAI GPT"
    override val requiresApiKey: Boolean = true
    override val isRateLimited: Boolean = true
    override val isOffline: Boolean = false

    override val supportedLanguages: List<Pair<String, String>> = LanguageCodes.COMMON_LANGUAGES

    private val json = Json { ignoreUnknownKeys = true }

    private val apiUrl = "https://api.openai.com/v1/chat/completions"

    @Serializable
    private data class ChatRequest(
        val model: String = "gpt-3.5-turbo",
        val messages: List<Message>,
        val temperature: Double = 0.3,
        @SerialName("max_tokens")
        val maxTokens: Int = 4096,
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatResponse(
        val choices: List<Choice>,
        val error: ErrorInfo? = null,
    )

    @Serializable
    private data class Choice(
        val message: Message,
    )

    @Serializable
    private data class ErrorInfo(
        val message: String,
        val type: String? = null,
        val code: String? = null,
    )

    override fun isConfigured(): Boolean {
        return preferences.openAiApiKey().get().isNotBlank()
    }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = preferences.openAiApiKey().get()

        if (apiKey.isBlank()) {
            return@withContext TranslationResult.Error(
                "OpenAI API key not configured",
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
        val sourceLangName = LanguageCodes.getDisplayName(sourceLanguage)
        val targetLangName = LanguageCodes.getDisplayName(targetLanguage)

        val systemPrompt = """You are a professional translator specializing in novel/fiction translation. Translate the following text from $sourceLangName to $targetLangName.
Rules:
- Only output the translation, nothing else
- Preserve paragraph structure (keep empty lines between paragraphs)
- Maintain the author's writing style and tone
- Keep character names consistent
- Do not add explanations or notes"""

        val request = ChatRequest(
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = text),
            ),
        )

        val requestBody = json.encodeToString(ChatRequest.serializer(), request)

        val httpRequest = Request.Builder()
            .url(apiUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(httpRequest).execute()
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
                val errorResponse = json.decodeFromString(ChatResponse.serializer(), responseBody)
                errorResponse.error?.message ?: "HTTP ${response.code}"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBody"
            }

            throw TranslationException(errorMessage, errorCode)
        }

        val chatResponse = json.decodeFromString(ChatResponse.serializer(), responseBody)
        return chatResponse.choices.firstOrNull()?.message?.content
            ?: throw TranslationException("Empty response from OpenAI", TranslationResult.ErrorCode.UNKNOWN)
    }

    private class TranslationException(
        message: String,
        val errorCode: TranslationResult.ErrorCode,
    ) : Exception(message)

    companion object {
        const val ENGINE_ID = 2L
    }
}
