package eu.kanade.tachiyomi.data.track.comick.dto

import kotlinx.serialization.Serializable

@Serializable
data class ComickUser(
    val id: String,
    val username: String?,
)
