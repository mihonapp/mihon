package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMOAuth
import eu.kanade.tachiyomi.data.track.bangumi.dto.isExpired
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class BangumiInterceptor(private val bangumi: Bangumi) : Interceptor {

    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: BGMOAuth? = bangumi.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currAuth: BGMOAuth = oauth ?: throw Exception("Not authenticated with Bangumi")

        if (currAuth.isExpired()) {
            val response = chain.proceed(BangumiApi.refreshTokenRequest(currAuth.refreshToken!!))
            if (response.isSuccessful) {
                currAuth = json.decodeFromString<BGMOAuth>(response.body.string())
                newAuth(currAuth)
            } else {
                response.close()
            }
        }

        return originalRequest.newBuilder()
            .header(
                "User-Agent",
                "antsylich/Mihon/v${BuildConfig.VERSION_NAME} (Android) (http://github.com/mihonapp/mihon)",
            )
            .apply {
                addHeader("Authorization", "Bearer ${currAuth.accessToken}")
            }
            .build()
            .let(chain::proceed)
    }

    fun newAuth(oauth: BGMOAuth?) {
        this.oauth = if (oauth == null) {
            null
        } else {
            BGMOAuth(
                oauth.accessToken,
                oauth.tokenType,
                System.currentTimeMillis() / 1000,
                oauth.expiresIn,
                oauth.refreshToken,
                this.oauth?.userId,
            )
        }

        bangumi.saveToken(oauth)
    }
}
