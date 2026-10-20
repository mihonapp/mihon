package eu.kanade.tachiyomi.data.track.mangaupdates

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class MangaUpdatesInterceptor(
    mangaUpdates: MangaUpdates,
) : Interceptor {

    private var token: String? = mangaUpdates.restoreSession()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = token ?: throw IOException("Not authenticated with MangaUpdates")

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(token: String?) {
        this.token = token
    }
}
