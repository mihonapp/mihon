package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HKOAuth(
    @SerialName("secret")
    val accessToken: String,

    @SerialName("expiration")
    val expiration: Long,
) {
    fun isExpired(): Boolean {
        return (expiration - 1000) < System.currentTimeMillis() / 1000
    }
}
