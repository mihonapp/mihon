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
        return (expiration - 43200) < (System.currentTimeMillis() / 1000)
    }
}
