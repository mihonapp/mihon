package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MyAnimeListInterceptor(private val myanimelist: MyAnimeList) : Interceptor {

    private val json: Json by injectLazy()

    private var oauth: OAuth? = myanimelist.loadOAuth()
    private val tokenExpired get() = myanimelist.getIfAuthExpired()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) {
            throw MALTokenExpired()
        }
        val originalRequest = chain.request()

        if (oauth?.isExpired() == true) {
            refreshToken(chain)
        }

        if (oauth == null) {
            throw IOException("MAL: User is not authenticated")
        }

        // Add the authorization header to the original request
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${oauth!!.access_token}")
            // TODO(antsy): Add back custom user agent when they stop blocking us for no apparent reason
            // .header("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: OAuth?) {
        this.oauth = oauth
        myanimelist.saveOAuth(oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): OAuth = synchronized(this) {
        if (tokenExpired) throw MALTokenExpired()
        oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

        val response = try {
            chain.proceed(MyAnimeListApi.refreshTokenRequest(oauth!!))
        } catch (_: Throwable) {
            throw MALTokenRefreshFailed()
        }

        if (response.code == 401) {
            myanimelist.setAuthExpired()
            throw MALTokenExpired()
        }

        return runCatching {
            if (response.isSuccessful) {
                with(json) { response.parseAs<OAuth>() }
            } else {
                response.close()
                null
            }
        }
            .getOrNull()
            ?.also {
                this.oauth = it
                myanimelist.saveOAuth(it)
            }
            ?: throw MALTokenRefreshFailed()
    }
}

class MALTokenRefreshFailed : IOException("MAL: Failed to refresh account token")
class MALTokenExpired : IOException("MAL: Login has expired")
