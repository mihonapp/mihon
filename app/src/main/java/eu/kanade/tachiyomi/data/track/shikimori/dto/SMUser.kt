package eu.kanade.tachiyomi.data.track.shikimori.dto

import kotlinx.serialization.Serializable

@Serializable
data class SMUserResult(
    val data: SMCurrentUser,
)

@Serializable
data class SMCurrentUser(
    val currentUser: SMUser,
)

@Serializable
data class SMUser(
    val id: String,
)
