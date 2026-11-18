package eu.kanade.tachiyomi.data.track.comick.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Serializable
data class ComickOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("refresh_token")
    val refreshToken: String,
    val scope: String,
) {
    fun isExpired() = Clock.System.now().plus(1.minutes).epochSeconds >= expiresAt
}
