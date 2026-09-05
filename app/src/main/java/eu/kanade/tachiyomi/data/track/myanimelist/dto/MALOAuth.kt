package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Serializable
data class MALOAuth(
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("created_at")
    @EncodeDefault
    val createdAt: Long = Clock.System.now().epochSeconds,
) {
    // Assumes expired a minute earlier
    fun isExpired() = Clock.System.now().plus(1.minutes).epochSeconds <= createdAt + expiresIn
}
