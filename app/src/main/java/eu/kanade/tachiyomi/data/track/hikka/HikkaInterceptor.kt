package eu.kanade.tachiyomi.data.track.hikka

import eu.kanade.tachiyomi.data.track.hikka.dto.HKAuthTokenInfo
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class HikkaInterceptor(private val hikka: Hikka) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile
    private var oauth: HKOAuth? = hikka.loadOAuth()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currAuth = oauth ?: throw Exception("Hikka: You are not authorized")

        if (currAuth.isExpired()) {
            currAuth = refreshToken(chain, currAuth)
        }

        val authRequest = originalRequest.newBuilder()
            .addHeader("auth", currAuth.accessToken)
            .addHeader("accept", "application/json")
            .build()

        return chain.proceed(authRequest)
    }

    private fun refreshToken(chain: Interceptor.Chain, expired: HKOAuth): HKOAuth = synchronized(this) {
        val currAuth = oauth ?: expired
        if (!currAuth.isExpired()) return@synchronized currAuth

        val refreshTokenResponse = chain.proceed(HikkaApi.refreshTokenRequest(currAuth.accessToken))
        if (!refreshTokenResponse.isSuccessful) {
            refreshTokenResponse.close()
            hikka.logout()
            throw Exception("Hikka: The token is expired")
        }
        refreshTokenResponse.close()

        val authTokenInfoResponse = chain.proceed(HikkaApi.authTokenInfo(currAuth.accessToken))
        if (!authTokenInfoResponse.isSuccessful) {
            authTokenInfoResponse.close()
            throw Exception("Hikka: Auth token info failed")
        }

        val authTokenInfo = with(json) {
            authTokenInfoResponse.parseAs<HKAuthTokenInfo>()
        }
        HKOAuth(currAuth.accessToken, authTokenInfo.expiration, authTokenInfo.created).also(::setAuth)
    }

    fun setAuth(oauth: HKOAuth?) {
        this.oauth = oauth
        hikka.saveOAuth(oauth)
    }
}
