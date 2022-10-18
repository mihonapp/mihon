package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.network.parseAs
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class MyAnimeListInterceptor(private val myanimelist: MyAnimeList, private var token: String?) : Interceptor {

    private var oauth: OAuth? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            throw IOException("Not authenticated with MyAnimeList")
        }
        if (oauth == null) {
            oauth = myanimelist.loadOAuth()
        }
        // Refresh access token if expired
        if (oauth != null && oauth!!.isExpired()) {
            val newOauth = runCatching {
                val oauthResponse = chain.proceed(MyAnimeListApi.refreshTokenRequest(oauth!!))

                if (oauthResponse.isSuccessful) {
                    oauthResponse.parseAs<OAuth>()
                } else {
                    oauthResponse.close()
                    null
                }
            }

            if (newOauth.getOrNull() == null) {
                throw IOException("Failed to refresh the access token")
            }

            setAuth(newOauth.getOrNull())
        }
        if (oauth == null) {
            throw IOException("No authentication token")
        }

        // Add the authorization header to the original request
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.access_token}")
            .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: OAuth?) {
        token = oauth?.access_token
        this.oauth = oauth
        myanimelist.saveOAuth(oauth)
    }
}
