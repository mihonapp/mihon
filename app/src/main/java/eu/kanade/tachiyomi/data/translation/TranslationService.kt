package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.domain.translation.model.TranslationLanguage

interface TranslationService {
    /**
     * Translates [texts] as a batch, returning one translation per entry and in the same order.
     */
    suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String>
}

enum class TranslationError {
    PROVIDER_ERROR,
    MISSING_API_KEY,
    UNSUPPORTED_SOURCE_SCRIPT,
    MISSING_TARGET_LANGUAGE,
    NO_TEXT_FOUND,
    NO_TRANSLATION,
    PAGE_NOT_AVAILABLE,
    SOURCE_LANGUAGE_REQUIRED,
}

class TranslationException(
    val error: TranslationError,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: error.name, cause)

internal suspend fun OkHttpClient.executeForBody(request: Request): String {
    val response = newCall(request).await()
    return response.use {
        if (!it.isSuccessful) {
            val body = runCatching { it.body.string() }.getOrDefault("")
            val detail = body.readableError().ifBlank { "HTTP ${it.code}" }
            throw TranslationException(TranslationError.PROVIDER_ERROR, detail)
        }
        it.body.string()
    }
}

/**
 * Providers answer failures with a wall of JSON. The message inside it is what the reader needs to see, not the
 * walls of braces the toast used to show.
 */
private fun String.readableError(): String {
    val element = runCatching { Json.parseToJsonElement(this) }.getOrNull()
    if (element is JsonObject) {
        val error = element["error"]
        val message = when (error) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.content
            is JsonPrimitive -> error.content
            else -> null
        } ?: (element["message"] as? JsonPrimitive)?.content
            ?: (element["detail"] as? JsonPrimitive)?.content
        if (!message.isNullOrBlank()) return message
    }
    return trim().take(160)
}
