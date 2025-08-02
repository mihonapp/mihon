package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * To use [okhttp3.brotli.BrotliInterceptor] as a network interceptor,
 * add [IgnoreGzipInterceptor] right before it.
 *
 * This nullifies the transparent gzip of [okhttp3.internal.http.BridgeInterceptor]
 * so gzip and Brotli are explicitly handled by the [okhttp3.brotli.BrotliInterceptor].
 *
 * This hack causes OkHttp cache to store decompressed responses, thus very inefficient.
 */
class IgnoreGzipInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Accept-Encoding") != "gzip") {
            return chain.proceed(request)
        }
        val response = chain.proceed(
            request.newBuilder()
                .removeHeader("Accept-Encoding")
                .build(),
        )
        return response.newBuilder().request(
            response.request.newBuilder()
                .header("Accept-Encoding", "gzip")
                .build(),
        ).build()
    }
}
