package eu.kanade.tachiyomi.data.track.hikka

import android.util.Log
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class HikkaInterceptor(private val hikka: Hikka) : Interceptor {

    private val json: Json by injectLazy()
    private var oauth: HKOAuth? = hikka.loadOAuth()
    private val tokenExpired get() = hikka.getIfAuthExpired()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) {
            throw HKTokenExpired()
        }
        val originalRequest = chain.request()

        if (oauth?.isExpired() == true) {
            refreshToken(chain)
        }

        if (oauth == null) {
            throw IOException("Hikka.io: User is not authenticated")
        }

        val authRequest = originalRequest.newBuilder()
            .addHeader("auth", oauth!!.accessToken)
            .addHeader("Cookie", "auth=${oauth!!.accessToken}")
            .addHeader("accept", "application/json")
            .build()

        Log.println(Log.WARN, "interceptor", "Set Auth Request Headers: " + authRequest.headers)

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: HKOAuth?) {
        this.oauth = oauth
        hikka.saveOAuth(oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): HKOAuth = synchronized(this) {
        if (tokenExpired) throw HKTokenExpired()
        oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

        val response = try {
            chain.proceed(HikkaApi.refreshTokenRequest(oauth!!))
        } catch (_: Throwable) {
            throw HKTokenRefreshFailed()
        }

        if (response.code == 401) {
            hikka.setAuthExpired()
            throw HKTokenExpired()
        }

        return runCatching {
            if (response.isSuccessful && oauth != null) {
                val responseBody = response.body?.string() ?: return@runCatching null
                val jsonObject = JSONObject(responseBody)

                val secret = oauth!!.accessToken
                val expiration = jsonObject.getLong("expiration")

                HKOAuth(secret, expiration)
            } else {
                response.close()
                null
            }
        }.getOrNull()?.also {
            this.oauth = it
            hikka.saveOAuth(it)
        } ?: throw HKTokenRefreshFailed()
    }
}

class HKTokenRefreshFailed : IOException("Hikka.io: Failed to refresh account token")
class HKTokenExpired : IOException("Hikka.io: Login has expired")
