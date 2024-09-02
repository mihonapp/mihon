package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class MUUrl(
    val original: String? = null,
    val thumb: String? = null,
)
