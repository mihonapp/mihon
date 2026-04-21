package eu.kanade.tachiyomi.data.track.yamtrack

import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class YamtrackInterceptor(private val yamtrack: Yamtrack) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = yamtrack.getApiToken()
        if (token.isBlank()) {
            throw IOException("Not authenticated with Yamtrack")
        }
        return chain.proceed(applyAuthHeaders(chain.request().newBuilder(), token).build())
    }

    companion object {
        fun applyAuthHeaders(builder: Request.Builder, token: String): Request.Builder {
            return builder
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Komikku v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
                .header("Accept", "application/json")
        }
    }
}
