package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val url: Url? = null,
    val height: Int? = null,
    val width: Int? = null,
)
