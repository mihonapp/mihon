package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class KitsuOAuth(
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
    fun isExpired(): Boolean = (Clock.System.now().toEpochMilliseconds() / 1000) > (createdAt + expiresIn - 3600)
}
