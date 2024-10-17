package eu.kanade.tachiyomi.data.track.hikka

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
            throw IOException("User is not authenticated")
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

    private fun refreshToken(chain: Interceptor.Chain): HKOAuth = synchronized(this) {
        if (tokenExpired) throw HKTokenExpired()
        oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

        val response = try {
            chain.proceed(HikkaApi.refreshTokenRequest(oauth!!))
        } catch (_: Throwable) {
            throw HKTokenRefreshFailed()
        }

        if (response.code != 200) {
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

class HKTokenRefreshFailed : IOException("Hikka: Failed to refresh account token")
class HKTokenExpired : IOException("Hikka: Login has expired")
