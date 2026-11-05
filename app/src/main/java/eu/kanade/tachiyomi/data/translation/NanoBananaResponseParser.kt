package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Pure parsing of Gemini `generateContent` responses. It has no Android dependencies so it can be unit tested on
 * the JVM. Nothing here logs or echoes request data, only short error messages from Google.
 */
object NanoBananaResponseParser {

    internal val SAFETY_FINISH_REASONS = setOf(
        "SAFETY",
        "IMAGE_SAFETY",
        "IMAGE_PROHIBITED_CONTENT",
        "PROHIBITED_CONTENT",
        "BLOCKLIST",
        "SPII",
        "RECITATION",
        "IMAGE_RECITATION",
    )

    private const val MAX_DETAIL_LENGTH = 300
    private const val MAX_RETRY_AFTER_MILLIS = 60_000L
    private val RETRY_DELAY_REGEX = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"")

    /**
     * Extracts the translated image from a successful (HTTP 200) response body.
     *
     * @throws TranslationException.ContentBlocked when safety filters blocked the request or the answer.
     * @throws TranslationException.NoImageReturned when the answer contains no usable image part.
     */
    fun parseImage(body: String, json: Json): TranslatedImage {
        val response = try {
            json.decodeFromString<GenerateContentResponse>(body)
        } catch (_: IllegalArgumentException) {
            throw TranslationException.NoImageReturned("Unreadable response")
        }

        response.promptFeedback?.blockReason?.takeIf { it.isNotBlank() }?.let {
            throw TranslationException.ContentBlocked(it)
        }

        val candidate = response.candidates?.firstOrNull()
            ?: throw TranslationException.NoImageReturned("No candidates")

        val parts = candidate.content?.parts.orEmpty()
        val imageParts = parts.filter { !it.inlineData?.data.isNullOrBlank() }
        // Thinking models can emit interim images flagged as thoughts. Prefer the final one.
        val imagePart = imageParts.lastOrNull { it.thought != true } ?: imageParts.lastOrNull()

        if (imagePart == null) {
            val finishReason = candidate.finishReason
            if (finishReason != null && finishReason in SAFETY_FINISH_REASONS) {
                throw TranslationException.ContentBlocked(finishReason)
            }
            val text = parts.mapNotNull { it.text }.joinToString(" ").trim().take(MAX_DETAIL_LENGTH)
            val detail = listOfNotNull(finishReason, text.ifEmpty { null }).joinToString(": ")
            throw TranslationException.NoImageReturned(detail.ifEmpty { "Empty answer" })
        }

        val inlineData = imagePart.inlineData!!
        val bytes = try {
            Base64.getMimeDecoder().decode(inlineData.data!!)
        } catch (_: IllegalArgumentException) {
            throw TranslationException.NoImageReturned("Invalid image data")
        }
        if (bytes.isEmpty()) throw TranslationException.NoImageReturned("Empty image data")

        return TranslatedImage(bytes, inlineData.mimeType ?: "image/png")
    }

    /**
     * Maps a non-successful HTTP response to a [TranslationException].
     *
     * The 429 status is used by Google for both short rate limits (retryable) and exhausted daily quota
     * (not retryable). The two are told apart by looking for per-day / zero-limit markers in the error body.
     * This is a heuristic.
     */
    fun parseError(httpCode: Int, body: String, json: Json, retryAfterHeader: String? = null): TranslationException {
        val message = try {
            json.decodeFromString<ApiErrorResponse>(body).error?.message
        } catch (_: IllegalArgumentException) {
            null
        }?.take(MAX_DETAIL_LENGTH)
        val lower = body.lowercase()

        return when (httpCode) {
            400 -> if ("api key" in lower && ("not valid" in lower || "invalid" in lower || "expired" in lower)) {
                TranslationException.InvalidApiKey(message)
            } else {
                TranslationException.RequestRejected(httpCode, message)
            }
            401 -> TranslationException.InvalidApiKey(message)
            403 -> if ("billing" in lower) {
                TranslationException.QuotaExceeded(message)
            } else {
                TranslationException.InvalidApiKey(message)
            }
            404 -> TranslationException.ModelNotFound(message)
            429 -> if (isQuotaExhausted(lower)) {
                TranslationException.QuotaExceeded(message)
            } else {
                TranslationException.RateLimited(parseRetryAfterMillis(body, retryAfterHeader))
            }
            in 500..599 -> TranslationException.ServerError(httpCode)
            else -> TranslationException.RequestRejected(httpCode, message)
        }
    }

    private fun isQuotaExhausted(lowerBody: String): Boolean {
        return "perday" in lowerBody ||
            "per day" in lowerBody ||
            "daily" in lowerBody ||
            "limit: 0" in lowerBody
    }

    private fun parseRetryAfterMillis(body: String, header: String?): Long? {
        val seconds = RETRY_DELAY_REGEX.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: header?.trim()?.toDoubleOrNull()
            ?: return null
        return (seconds * 1000).toLong().coerceIn(0L, MAX_RETRY_AFTER_MILLIS)
    }
}
