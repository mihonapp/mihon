package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Supplies the legacy default user agent when an extension request omits one. */
class UserAgentInterceptor(
    private val defaultUserAgent: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = request.header("User-Agent")
        val updated = if (userAgent.isNullOrBlank()) {
            request.newBuilder().header("User-Agent", defaultUserAgent()).build()
        } else {
            request
        }
        return chain.proceed(updated)
    }
}
