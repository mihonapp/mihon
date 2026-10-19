package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class BangumiInterceptor(private val bangumi: Bangumi) : Interceptor {

    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    @Volatile
    private var oauth: BGMOAuth? = bangumi.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currAuth: BGMOAuth = oauth ?: throw Exception("Not authenticated with Bangumi")

        if (currAuth.isExpired()) {
            currAuth = refreshToken(chain, currAuth)
        }

        return originalRequest.newBuilder()
            .header(
                "User-Agent",
                "antsylich/Mihon/v${BuildConfig.VERSION_NAME} (Android) (http://github.com/mihonapp/mihon)",
            )
            .addHeader("Authorization", "Bearer ${currAuth.accessToken}")
            .build()
            .let(chain::proceed)
    }

    private fun refreshToken(chain: Interceptor.Chain, expired: BGMOAuth): BGMOAuth = synchronized(this) {
        val current = oauth ?: expired
        if (!current.isExpired()) return@synchronized current

        val response = chain.proceed(BangumiApi.refreshTokenRequest(current.refreshToken!!))
        if (!response.isSuccessful) {
            response.close()
            return@synchronized current
        }
        with(json) { response.parseAs<BGMOAuth>() }.also(::newAuth)
    }

    fun newAuth(oauth: BGMOAuth?) {
        this.oauth = oauth
        bangumi.saveToken(oauth)
    }
}
