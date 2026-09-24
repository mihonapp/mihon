package eu.kanade.tachiyomi.data.track.bangumi.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("created_at")
    @EncodeDefault
    val createdAt: Long = Clock.System.now().epochSeconds,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String?,
) {
    // Access token refresh before expired
    fun isExpired() = Clock.System.now().plus(1.hours).epochSeconds > (createdAt + expiresIn)
}
