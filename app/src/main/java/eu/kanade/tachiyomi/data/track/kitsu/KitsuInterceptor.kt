package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class KitsuInterceptor(private val kitsu: Kitsu) : Interceptor {

    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    @Volatile
    private var oauth: KitsuOAuth? = kitsu.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currAuth = oauth ?: throw Exception("Not authenticated with Kitsu")

        // Refresh access token if expired, once for all the requests that found it expired.
        if (currAuth.isExpired()) {
            currAuth = synchronized(this) {
                val latest = oauth ?: throw Exception("Not authenticated with Kitsu")
                if (!latest.isExpired()) return@synchronized latest

                val response = chain.proceed(KitsuApi.refreshTokenRequest(latest.refreshToken!!))
                if (response.isSuccessful) {
                    with(json) { response.parseAs<KitsuOAuth>() }.also(::newAuth)
                } else {
                    response.close()
                    latest
                }
            }
        }

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${currAuth.accessToken}")
            .header("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json")
            .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: KitsuOAuth?) {
        this.oauth = oauth
        kitsu.saveToken(oauth)
    }
}
