package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALUser(
    val id: Int,
    val name: String,
    val scoreFormat: String,
)
