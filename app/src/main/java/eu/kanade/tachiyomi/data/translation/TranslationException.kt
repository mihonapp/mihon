package eu.kanade.tachiyomi.data.translation

/**
 * Typed failures of the chapter translation feature.
 *
 * Messages are for logs only. They never contain the API key or image data.
 *
 * - [isFatal]: the whole job must stop (the same error would hit every other page).
 * - [maxRetries]: how many times the client retries the request before giving up.
 * - Everything that is neither fatal nor retryable only fails the current page.
 */
sealed class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    open val isFatal: Boolean get() = false
    open val maxRetries: Int get() = 0
    open val retryAfterMillis: Long? get() = null

    // region API errors

    /** HTTP 401/403 or "API key not valid". */
    class InvalidApiKey(detail: String?) : TranslationException("API key rejected: ${detail.orEmpty()}") {
        override val isFatal get() = true
    }

    /** Daily quota or billing problems. Retrying cannot help. */
    class QuotaExceeded(detail: String?) : TranslationException("Quota or billing limit: ${detail.orEmpty()}") {
        override val isFatal get() = true
    }

    /** HTTP 404, usually a wrong or retired model id. */
    class ModelNotFound(detail: String?) : TranslationException("Model not found: ${detail.orEmpty()}") {
        override val isFatal get() = true
    }

    class UnsupportedCapability(detail: String) : TranslationException("Unsupported provider capability: $detail") {
        override val isFatal get() = true
    }

    /** Any other 4xx. */
    class RequestRejected(val httpCode: Int, val detail: String?) :
        TranslationException("Request rejected (HTTP $httpCode): ${detail.orEmpty()}") {
        override val isFatal get() = true
    }

    /** HTTP 429 that is a per-minute style limit. Retryable. */
    class RateLimited(override val retryAfterMillis: Long? = null) : TranslationException("Rate limited") {
        override val maxRetries get() = 3
    }

    /** HTTP 5xx. Retryable. */
    class ServerError(val httpCode: Int) : TranslationException("Server error (HTTP $httpCode)") {
        override val maxRetries get() = 3
    }

    /** Timeouts and dropped connections. Retryable. */
    class NetworkError(cause: Throwable) : TranslationException("Network error: ${cause.javaClass.simpleName}", cause) {
        override val maxRetries get() = 3
    }

    /** Still no network after retries. Stops the job so WorkManager can resume it later. */
    class Offline(cause: Throwable? = null) : TranslationException("No network connection", cause) {
        override val isFatal get() = true
    }

    // endregion

    // region Page level errors (the job continues with the other pages)

    /** Safety filters blocked the prompt or the image. */
    class ContentBlocked(val reason: String) : TranslationException("Blocked by content filters: $reason")

    /** The model answered without an image part. Retried once. */
    class NoImageReturned(detail: String?) : TranslationException("No image in response: ${detail.orEmpty()}") {
        override val maxRetries get() = 1
    }

    /** The text model answered, but not with the JSON the overlay mode expects. Retried twice. */
    class UnreadableAnswer(detail: String?) : TranslationException("Unreadable answer: ${detail.orEmpty()}") {
        override val maxRetries get() = 2
    }

    /** The model hit its output token limit, so the returned box list may be incomplete. */
    class OutputTruncated : TranslationException("Model response reached the output token limit")

    /** A page that cannot be read or decoded. */
    class CorruptPage(detail: String, cause: Throwable? = null) :
        TranslationException("Unreadable page: $detail", cause)

    // endregion

    // region Chapter / storage errors

    class EmptyChapter(detail: String) : TranslationException("Chapter has no pages: $detail") {
        override val isFatal get() = true
    }

    class CorruptChapter(cause: Throwable) : TranslationException("Chapter archive is unreadable", cause) {
        override val isFatal get() = true
    }

    class NotEnoughStorage(val requiredBytes: Long, val availableBytes: Long) :
        TranslationException("Not enough free storage") {
        override val isFatal get() = true
    }

    class StorageWriteFailed(cause: Throwable?) : TranslationException("Failed to write output", cause) {
        override val isFatal get() = true
    }

    class LocalSourceUnavailable : TranslationException("Local source folder is unavailable") {
        override val isFatal get() = true
    }

    // endregion
}
