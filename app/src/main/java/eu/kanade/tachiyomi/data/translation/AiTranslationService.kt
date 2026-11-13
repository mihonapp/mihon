package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.domain.translation.model.TranslationProvider
import tachiyomi.domain.translation.service.TranslationPreferences
import java.util.Locale

/**
 * Translates through a chat model the user brings their own key for.
 *
 * The whole page goes out as one JSON array, so the model sees the bubbles next to each other and keeps the
 * pronouns, the names and the tone consistent, which is where the free endpoints fall apart on Japanese and
 * Korean dialogue.
 */
class AiTranslationService(
    private val networkHelper: NetworkHelper,
    private val preferences: TranslationPreferences,
) : TranslationService {

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()

        val backend = backendFor(preferences.provider.get())
        val key = apiKey(backend)
        if (key.isBlank()) throw TranslationException(TranslationError.MISSING_API_KEY)
        val model = model(backend)
        val language = languageInstruction(source, target)

        val answer = try {
            requestBatch(backend, key, model, language, texts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        answer?.let { raw ->
            parseArray(raw, texts.size)?.let { return it }
        }

        // A model that answered in prose, or dropped an entry, still gets a chance bubble by bubble.
        return texts.map { text ->
            if (text.isBlank()) {
                text
            } else {
                requestSingle(backend, key, model, language, text).trim()
            }
        }
    }

    /** One request for the whole page, answered with a JSON array in the same order. */
    private suspend fun requestBatch(
        backend: AiBackend,
        key: String,
        model: String,
        language: String,
        texts: List<String>,
    ): String {
        val payload = buildJsonArray { texts.forEach { add(JsonPrimitive(it)) } }.toString()
        val system = "You translate comic dialogue. $language " +
            "The user sends a JSON array of speech bubbles. Answer with only a JSON array of the same length, " +
            "holding the translation of each entry in the same order. No commentary, no code fences."
        return send(backend, key, model, system, payload)
    }

    private suspend fun requestSingle(
        backend: AiBackend,
        key: String,
        model: String,
        language: String,
        text: String,
    ): String {
        val system = "You translate comic dialogue. $language Answer with the translation alone, nothing else."
        return send(backend, key, model, system, text)
    }

    private suspend fun send(
        backend: AiBackend,
        key: String,
        model: String,
        system: String,
        user: String,
    ): String {
        val request = when (backend) {
            AiBackend.OPENAI -> openAiRequest(key, model, system, user)
            AiBackend.GEMINI -> geminiRequest(key, model, system, user)
            AiBackend.CLAUDE -> claudeRequest(key, model, system, user)
        }
        val body = networkHelper.client.executeForBody(request)
        return when (backend) {
            AiBackend.OPENAI -> body.jsonPath("choices", 0, "message", "content")
            AiBackend.GEMINI -> body.jsonPath("candidates", 0, "content", "parts", 0, "text")
            AiBackend.CLAUDE -> body.jsonPath("content", 0, "text")
        } ?: throw TranslationException(TranslationError.PROVIDER_ERROR, "Unexpected response from $backend")
    }

    private fun openAiRequest(key: String, model: String, system: String, user: String): Request {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }
        return Request.Builder()
            .url("$OPENAI_ENDPOINT/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", JSON)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun geminiRequest(key: String, model: String, system: String, user: String): Request {
        val body = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
            putJsonObject("generationConfig") { put("temperature", 0.2) }
        }
        return Request.Builder()
            .url("$GEMINI_ENDPOINT/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .header("Content-Type", JSON)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun claudeRequest(key: String, model: String, system: String, user: String): Request {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("temperature", 0.2)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }
        return Request.Builder()
            .url("$CLAUDE_ENDPOINT/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", JSON)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    /**
     * Models like to wrap their answer in a sentence or a code fence, so the array is cut out of the text.
     */
    private fun parseArray(raw: String, expected: Int): List<String>? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null

        val array = runCatching {
            Json.Default.parseToJsonElement(raw.substring(start, end + 1)).jsonArray
        }.getOrNull() ?: return null
        if (array.size != expected) return null

        return array.map { element ->
            when (element) {
                is JsonPrimitive -> element.content
                else -> return null
            }
        }
    }

    /**
     * Walks the response object by key, with numbers stepping into arrays.
     */
    private fun String.jsonPath(vararg path: Any): String? {
        var current: JsonElement = runCatching { Json.Default.parseToJsonElement(this) }.getOrNull() ?: return null
        for (step in path) {
            val node = current
            current = when (node) {
                is JsonObject -> (step as? String)?.let { node[it] }
                is JsonArray -> (step as? Int)?.let { node.getOrNull(it) }
                else -> null
            } ?: return null
        }
        return (current as? JsonPrimitive)?.content
    }

    private fun backendFor(provider: TranslationProvider): AiBackend = when (provider) {
        TranslationProvider.OPENAI -> AiBackend.OPENAI
        TranslationProvider.GEMINI -> AiBackend.GEMINI
        TranslationProvider.CLAUDE -> AiBackend.CLAUDE
        else -> throw TranslationException(TranslationError.PROVIDER_ERROR, "Not an AI provider")
    }

    private fun apiKey(backend: AiBackend): String = when (backend) {
        AiBackend.OPENAI -> preferences.openAiApiKey.get()
        AiBackend.GEMINI -> preferences.geminiApiKey.get()
        AiBackend.CLAUDE -> preferences.claudeApiKey.get()
    }.cleanKey()

    /** Pasting a key often brings quotes or a zero-width space along, and the provider then rejects it. */
    private fun String.cleanKey(): String = filter { it.code in 33..126 }.trim()

    private fun model(backend: AiBackend): String = when (backend) {
        AiBackend.OPENAI -> preferences.openAiModel.get().ifBlank { DEFAULT_OPENAI_MODEL }
        AiBackend.GEMINI -> preferences.geminiModel.get().ifBlank { DEFAULT_GEMINI_MODEL }
        AiBackend.CLAUDE -> preferences.claudeModel.get().ifBlank { DEFAULT_CLAUDE_MODEL }
    }.trim()

    private fun languageInstruction(source: TranslationLanguage, target: TranslationLanguage): String {
        val from = if (source == TranslationLanguage.AUTO) {
            "the language it is written in"
        } else {
            source.localeName()
        }
        return "Translate from $from to ${target.localeName()}."
    }

    private fun TranslationLanguage.localeName(): String {
        val name = Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH)
        return name.ifBlank { code }
    }

    private enum class AiBackend { OPENAI, GEMINI, CLAUDE }

    companion object {
        private const val OPENAI_ENDPOINT = "https://api.openai.com/v1"
        private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta"
        private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1"

        private const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        private const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
        private const val DEFAULT_CLAUDE_MODEL = "claude-3-5-haiku-latest"

        private const val JSON = "application/json"
        private val JSON_MEDIA = JSON.toMediaType()
    }
}
