package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Serializable
data class MangaBakaOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("token_type")
    val tokenType: String,
    val scope: String,
) {
    fun isExpired(): Boolean = Clock.System.now().plus(1.minutes).epochSeconds > expiresAt
}
