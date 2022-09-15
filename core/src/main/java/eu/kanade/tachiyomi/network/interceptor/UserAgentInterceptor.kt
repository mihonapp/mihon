package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class UserAgentInterceptor : Interceptor {

    private val networkHelper: NetworkHelper by injectLazy()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            val newRequest = originalRequest
                .newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", networkHelper.defaultUserAgent)
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
