package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class MUCurrentUser(
    val username: String,
)
