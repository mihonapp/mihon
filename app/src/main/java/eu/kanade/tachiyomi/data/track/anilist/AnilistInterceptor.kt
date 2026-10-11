package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AnilistInterceptor(val anilist: Anilist, private var token: String?) : Interceptor {

    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: ALOAuth? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            throw Exception("Not authenticated with Anilist")
        }
        if (oauth == null) {
            oauth = anilist.loadOAuth() ?: throw IOException("No authentication token")
        }
        if (oauth!!.isExpired()) {
            anilist.logout()
            throw IOException("Token expired. Reconnect AniList in Settings.")
        }

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
            .header("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with Anilist for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: ALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        anilist.saveOAuth(oauth)
    }
}
