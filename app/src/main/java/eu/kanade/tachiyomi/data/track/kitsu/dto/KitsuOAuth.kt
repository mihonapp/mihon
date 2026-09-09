package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Serializable
data class KitsuOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String?,
) {
    fun isExpired() = Clock.System.now().plus(1.hours).epochSeconds > (createdAt + expiresIn)
}
