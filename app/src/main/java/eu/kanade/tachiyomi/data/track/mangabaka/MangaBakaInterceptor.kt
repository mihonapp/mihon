package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class MangaBakaInterceptor(private val mangaBaka: MangaBaka) : Interceptor {

    private val json: Json by injectLazy()

    @Volatile
    private var oauth: MangaBakaOAuth? = mangaBaka.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currentAuth = oauth ?: throw Exception("Not authenticated with MangaBaka")

        // Refresh once for all the requests that found the token expired
        if (currentAuth.isExpired()) {
            currentAuth = synchronized(this) {
                val latest = oauth ?: throw Exception("Not authenticated with MangaBaka")
                if (!latest.isExpired()) return@synchronized latest

                val response = chain.proceed(MangaBakaApi.refreshTokenRequest(latest.refreshToken))
                if (response.isSuccessful) {
                    with(json) { response.parseAs<MangaBakaOAuth>() }.also(::setAuth)
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

    fun setAuth(oauth: MangaBakaOAuth?) {
        this.oauth = oauth

        mangaBaka.saveToken(oauth)
    }
}
