package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class MangaBakaInterceptor(private val mangaBaka: MangaBaka) : Interceptor {

    private val json: Json by injectLazy()

    private var oauth: MangaBakaOAuth? = mangaBaka.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currentAuth = oauth ?: throw Exception("Not authenticated with MangaBaka")

        if (currentAuth.isExpired()) {
            val response = chain.proceed(MangaBakaApi.refreshTokenRequest(currentAuth.refreshToken))
            if (response.isSuccessful) {
                currentAuth = json.decodeFromString(response.body.string())
                setAuth(currentAuth)
            } else {
                response.close()
            }
        }

        return originalRequest.newBuilder()
            .header("User-Agent", "Mihon/v${BuildConfig.VERSION_NAME} (Android) (https://github.com/mihonapp/mihon)")
            .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
            .build()
            .let(chain::proceed)
    }

    fun setAuth(oauth: MangaBakaOAuth?) {
        this.oauth = oauth

        mangaBaka.saveToken(oauth)
    }
}
