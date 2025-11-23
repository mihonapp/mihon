package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AnilistInterceptor(
    private var token: AnilistToken?,
    private val onTokenExpired: () -> Unit,
    private val onAuthRevoked: () -> Unit,
) : Interceptor {

    fun updateToken(token: AnilistToken?) {
        this.token = token
    }

    @OptIn(ExperimentalTime::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = token ?: throw IOException("Not logged into AniList")
        val expiresAt = Instant.fromEpochSeconds(token.decoded.expiresAt).minus(1.minutes)

        if (expiresAt < Clock.System.now()) {
            onTokenExpired()
            throw IOException("AniList token expired")
        }

        val response = request.newBuilder()
            .addHeader("Authorization", "Bearer ${token.value}")
            .header("User-Agent", "Mihon v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()
            .let(chain::proceed)

        if (response.code == 401) {
            onAuthRevoked()
            throw IOException("Anilist auth revoked")
        }

        return response
    }
}
