package eu.kanade.tachiyomi.data.track.hikka

import eu.kanade.tachiyomi.data.track.hikka.dto.HKAuthTokenInfo
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class HikkaInterceptor(private val hikka: Hikka) : Interceptor {

    private val json: Json by injectLazy()
    private var oauth: HKOAuth? = hikka.loadOAuth()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val currAuth = oauth ?: throw Exception("Not authenticated with Hikka")

        if (currAuth.isExpired()) {
            val refreshTokenResponse = chain.proceed(HikkaApi.refreshTokenRequest(currAuth.accessToken))
            if (!refreshTokenResponse.isSuccessful)
                refreshTokenResponse.close()

            val authTokenInfoResponse = chain.proceed(HikkaApi.authTokenInfo(currAuth.accessToken))
            if (!authTokenInfoResponse.isSuccessful)
                authTokenInfoResponse.close()

            val authTokenInfo = json.decodeFromString<HKAuthTokenInfo>(authTokenInfoResponse.body.string())
            setAuth(HKOAuth(oauth!!.accessToken, authTokenInfo.expiration))
        }

        val authRequest = originalRequest.newBuilder()
            .addHeader("auth", oauth!!.accessToken)
            .addHeader("accept", "application/json")
            .build()

        return chain.proceed(authRequest)
    }

    fun setAuth(oauth: HKOAuth?) {
        this.oauth = oauth
        hikka.saveOAuth(oauth)
    }
}
