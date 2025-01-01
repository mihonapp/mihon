package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HKOAuth(
    @SerialName("secret") val accessToken: String,
    val expiration: Long,
    val created: Long,
) {
    fun isExpired(): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        val buffer = 5 * 60 // safety margin
        return currentTime >= (expiration - buffer)
    }
}
