package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Serializable
data class HKOAuth(
    @SerialName("secret")
    val accessToken: String,
    val expiration: Long,
    val created: Long,
) {
    fun isExpired() = Clock.System.now().plus(5.minutes).epochSeconds >= expiration
}
