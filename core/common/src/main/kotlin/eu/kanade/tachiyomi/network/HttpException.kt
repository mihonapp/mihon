package eu.kanade.tachiyomi.network

import okhttp3.Response

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 * Use it to have a standardized error message in the app across the extensions.
 *
 * @see Response.isSuccessful
 * @since tachiyomix 1.6
 * @param code [Int] the HTTP status code
 * @param retryAfter raw Retry-After response header, when available
 */
class HttpException : IllegalStateException {

    val code: Int
    val retryAfter: String?

    /** Kept as a real one-argument constructor for binary compatibility with installed sources. */
    constructor(code: Int) : this(code, null)

    constructor(code: Int, retryAfter: String?) : super("HTTP error $code") {
        this.code = code
        this.retryAfter = retryAfter
    }
}
