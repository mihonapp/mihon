package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class ShikimoriInterceptor(private val shikimori: Shikimori) : Interceptor {

    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: SMOAuth? = shikimori.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currAuth = oauth ?: throw Exception("Not authenticated with Shikimori")

        // Refresh access token if expired.
        if (currAuth.isExpired()) {
            val response = chain.proceed(ShikimoriApi.refreshTokenRequest(currAuth.refreshToken!!))
            if (response.isSuccessful) {
                currAuth = with(json) {
                    response.parseAs<SMOAuth>()
                }
                newAuth(currAuth)
            } else {
                response.close()
            }
        }
        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
            .header("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: SMOAuth?) {
        this.oauth = oauth
        shikimori.saveToken(oauth)
    }
}
