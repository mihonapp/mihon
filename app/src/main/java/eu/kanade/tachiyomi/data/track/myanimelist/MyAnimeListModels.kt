package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.data.database.models.Track
import kotlinx.serialization.Serializable

@Serializable
data class OAuth(
    val token_type: String,
    val refresh_token: String,
    val access_token: String,
    val expires_in: Long,
    val created_at: Long = System.currentTimeMillis(),
) {
    // Assumes expired a minute earlier
    private val adjustedExpiresIn: Long = (expires_in - 60) * 1000
    fun isExpired() = created_at + adjustedExpiresIn < System.currentTimeMillis()
}

fun Track.toMyAnimeListStatus() = when (status) {
    MyAnimeList.READING -> "reading"
    MyAnimeList.COMPLETED -> "completed"
    MyAnimeList.ON_HOLD -> "on_hold"
    MyAnimeList.DROPPED -> "dropped"
    MyAnimeList.PLAN_TO_READ -> "plan_to_read"
    MyAnimeList.REREADING -> "reading"
    else -> null
}

fun getStatus(status: String?) = when (status) {
    "reading" -> MyAnimeList.READING
    "completed" -> MyAnimeList.COMPLETED
    "on_hold" -> MyAnimeList.ON_HOLD
    "dropped" -> MyAnimeList.DROPPED
    "plan_to_read" -> MyAnimeList.PLAN_TO_READ
    else -> MyAnimeList.READING
}
