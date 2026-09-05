package eu.kanade.tachiyomi.data.track.bangumi.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
// Incomplete DTO with only our needed attributes
data class BGMOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("created_at")
    @EncodeDefault
    val createdAt: Long = System.currentTimeMillis() / 1000,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String?,
) {
    // Access token refresh before expired
    fun isExpired() = (System.currentTimeMillis() / 1000) > (createdAt + expiresIn - 3600)
}
