package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.await
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
            val detail = runCatching { it.body.string() }.getOrDefault("").take(200)
            throw TranslationException(TranslationError.PROVIDER_ERROR, detail.ifBlank { "HTTP ${it.code}" })
        }
        it.body.string()
    }
}
