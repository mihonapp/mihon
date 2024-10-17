package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.Serializable

@Serializable
data class HKOAuth(
    val accessToken: String,
    val expiration: Long,
) {
    fun isExpired(): Boolean {
        return (expiration - 43200) < (System.currentTimeMillis() / 1000)
    }
}
