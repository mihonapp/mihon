package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALOAuth(
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("created_at")
    @EncodeDefault
    val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    // Assumes expired a minute earlier
    private val adjustedExpiresIn: Long = (expiresIn - 60)

    fun isExpired() = createdAt + adjustedExpiresIn < System.currentTimeMillis() / 1000
}
