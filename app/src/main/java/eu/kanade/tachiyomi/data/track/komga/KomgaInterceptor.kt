package eu.kanade.tachiyomi.data.track.komga

import android.util.Base64
import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class KomgaInterceptor(private val komga: Komga) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val credentials = Base64.encodeToString(
            "${komga.getUsername()}:${komga.getPassword()}".toByteArray(),
            Base64.NO_WRAP,
        )
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Basic $credentials")
            .header("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()
        return chain.proceed(authRequest)
    }
}
