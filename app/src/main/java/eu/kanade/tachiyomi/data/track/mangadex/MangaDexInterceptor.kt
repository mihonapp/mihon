package eu.kanade.tachiyomi.data.track.mangadex

import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexOAuth
import eu.kanade.tachiyomi.data.track.mangadex.dto.MangaDexTokenResponse
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class MangaDexInterceptor(private val tracker: MangaDex) : Interceptor {

    private val json: Json by injectLazy()

    @Volatile
    private var oauth: MangaDexOAuth? = tracker.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currentAuth = oauth ?: throw Exception("Not authenticated with MangaDex")

        if (currentAuth.isExpired()) {
            currentAuth = synchronized(this) {
                val latest = oauth ?: throw Exception("Not authenticated with MangaDex")
                if (!latest.isExpired()) return@synchronized latest

                val response = chain.proceed(MangaDexApi.refreshTokenRequest(latest))
                if (response.isSuccessful) {
                    with(json) {
                        response.parseAs<MangaDexTokenResponse>()
                    }.toOauth(latest.refreshToken).also(::setAuth)
                } else {
                    response.close()
                    latest
                }
            }
        }

        return originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
            .build()
            .let(chain::proceed)
    }

    fun setAuth(oauth: MangaDexOAuth?) {
        this.oauth = oauth

        tracker.saveToken(oauth)
    }
}
