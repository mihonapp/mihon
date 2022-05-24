package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class Status(
    val volume: Int? = null,
    val chapter: Int? = null,
)
