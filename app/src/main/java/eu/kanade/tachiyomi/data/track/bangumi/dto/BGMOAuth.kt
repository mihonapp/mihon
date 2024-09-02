package eu.kanade.tachiyomi.data.track.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BGMOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String?,
    @SerialName("user_id")
    val userId: Long?,
)

// Access token refresh before expired
fun BGMOAuth.isExpired() = (System.currentTimeMillis() / 1000) > (createdAt + expiresIn - 3600)
