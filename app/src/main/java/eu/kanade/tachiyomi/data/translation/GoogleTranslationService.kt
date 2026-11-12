package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import tachiyomi.domain.translation.model.TranslationLanguage

/**
 * Uses the endpoint behind Google Translate's web widget. It needs no key, but it's unofficial and only takes one
 * text per request.
 */
class GoogleTranslationService(
    private val networkHelper: NetworkHelper,
) : TranslationService {

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        return texts.mapIndexed { index, text ->
            if (text.isBlank()) {
                text
            } else {
                if (index > 0) delay(REQUEST_INTERVAL_MILLIS)
                translateWithRetry(text, source, target)
            }
        }
    }

    /**
     * The endpoint is unofficial and throttles bursts, so a single hiccup shouldn't drop a whole speech bubble.
     */
    private suspend fun translateWithRetry(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): String {
        var lastError: TranslationException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return translate(text, source, target)
            } catch (e: TranslationException) {
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MILLIS * (attempt + 1))
            }
        }
        throw lastError ?: TranslationException(TranslationError.PROVIDER_ERROR)
    }

    private suspend fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): String {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", source.googleCode())
            .addQueryParameter("tl", target.googleCode())
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", networkHelper.defaultUserAgentProvider())
            .build()

        val body = networkHelper.client.executeForBody(request)
        val sentences = runCatching {
            Json.Default.parseToJsonElement(body).jsonArray[0].jsonArray
        }.getOrNull() ?: throw TranslationException(
            TranslationError.PROVIDER_ERROR,
            "Unexpected response from Google Translate",
        )

        return sentences.joinToString(separator = "") { sentence ->
            sentence.jsonArray.firstOrNull()?.jsonPrimitive?.content.orEmpty()
        }.ifBlank {
            throw TranslationException(TranslationError.PROVIDER_ERROR, "Empty translation from Google Translate")
        }
    }

    private fun TranslationLanguage.googleCode(): String = when (this) {
        TranslationLanguage.CHINESE_SIMPLIFIED -> "zh-CN"
        TranslationLanguage.CHINESE_TRADITIONAL -> "zh-TW"
        else -> code
    }

    companion object {
        private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        private const val REQUEST_INTERVAL_MILLIS = 100L
        private const val RETRY_DELAY_MILLIS = 400L
        private const val MAX_ATTEMPTS = 3
    }
}
