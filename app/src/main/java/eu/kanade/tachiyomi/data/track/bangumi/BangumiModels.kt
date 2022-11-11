package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.data.database.models.Track
import kotlinx.serialization.Serializable

@Serializable
data class Avatar(
    val large: String? = "",
    val medium: String? = "",
    val small: String? = "",
)

@Serializable
data class Collection(
    val `private`: Int? = 0,
    val comment: String? = "",
    val ep_status: Int? = 0,
    val lasttouch: Int? = 0,
    val rating: Float? = 0f,
    val status: Status? = Status(),
    val tag: List<String?>? = emptyList(),
    val user: User? = User(),
    val vol_status: Int? = 0,
)

@Serializable
data class Status(
    val id: Int? = 0,
    val name: String? = "",
    val type: String? = "",
)

@Serializable
data class User(
    val avatar: Avatar? = Avatar(),
    val id: Int? = 0,
    val nickname: String? = "",
    val sign: String? = "",
    val url: String? = "",
    val usergroup: Int? = 0,
    val username: String? = "",
)

@Serializable
data class OAuth(
    val access_token: String,
    val token_type: String,
    val created_at: Long = System.currentTimeMillis() / 1000,
    val expires_in: Long,
    val refresh_token: String?,
    val user_id: Long?,
)

// Access token refresh before expired
fun OAuth.isExpired() = (System.currentTimeMillis() / 1000) > (created_at + expires_in - 3600)

fun Track.toBangumiStatus() = when (status) {
    Bangumi.READING -> "do"
    Bangumi.COMPLETED -> "collect"
    Bangumi.ON_HOLD -> "on_hold"
    Bangumi.DROPPED -> "dropped"
    Bangumi.PLAN_TO_READ -> "wish"
    else -> throw NotImplementedError("Unknown status: $status")
}

fun toTrackStatus(status: String) = when (status) {
    "do" -> Bangumi.READING
    "collect" -> Bangumi.COMPLETED
    "on_hold" -> Bangumi.ON_HOLD
    "dropped" -> Bangumi.DROPPED
    "wish" -> Bangumi.PLAN_TO_READ
    else -> throw NotImplementedError("Unknown status: $status")
}
