package eu.kanade.tachiyomi.data.track.comick

import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class ComickInterceptor(private val comick: Comick) : Interceptor {

    private val json: Json by injectLazy()

    private var oauth: ComickOAuth? = comick.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currentAuth = oauth ?: throw Exception("Not authenticated with Comick")

        if (currentAuth.isExpired()) {
            val response = chain.proceed(ComickApi.refreshTokenRequest(currentAuth.refreshToken))
            if (response.isSuccessful) {
                currentAuth = with(json) {
                    response.parseAs<ComickOAuth>()
                }
                setAuth(currentAuth)
            } else {
                response.close()
            }
        }

        return originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
            .build()
            .let(chain::proceed)
    }

    fun setAuth(oauth: ComickOAuth?) {
        this.oauth = oauth

        comick.saveToken(oauth)
    }
}
