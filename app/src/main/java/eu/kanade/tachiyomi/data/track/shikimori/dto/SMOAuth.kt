package eu.kanade.tachiyomi.data.track.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class SMOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String?,
) {
    // Access token lives 1 day
    fun isExpired() = (Clock.System.now().toEpochMilliseconds() / 1000) > (createdAt + expiresIn - 3600)
}
