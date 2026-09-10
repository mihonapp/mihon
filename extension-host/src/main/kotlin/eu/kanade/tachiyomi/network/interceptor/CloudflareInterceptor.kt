package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Compatibility marker for extensions that require Mihon's default Cloudflare interceptor. */
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
