package eu.kanade.tachiyomi.data.track.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Avatar(
    val large: String? = "",
    val medium: String? = "",
    val small: String? = "",
)

@Serializable
data class User(
    val avatar: Avatar? = Avatar(),
    val id: Int? = 0,
    val nickname: String? = "",
    val sign: String? = "",
    val url: String? = "",
    @SerialName("usergroup")
    val userGroup: Int? = 0,
    val username: String? = "",
)
