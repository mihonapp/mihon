package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Serializable
data class ALOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @EncodeDefault
    val expires: Long = Clock.System.now().plus(365.days).toEpochMilliseconds(),
) {
    fun isExpired() = Clock.System.now().plus(1.minutes).toEpochMilliseconds() > expires
}
