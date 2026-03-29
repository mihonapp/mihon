package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaBakaItemResult(
    val data: MangaBakaItem,
)

@Serializable
data class MangaBakaSearchResult(
    val data: List<MangaBakaItem>,
)

@Serializable
data class MangaBakaItem(
    val id: Long,
    val title: String,
    val cover: MangaBakaCover,
    val authors: List<String>?,
    val artists: List<String>?,
    val description: String?,
    val year: Int?,
    val status: String,
    val type: String,
    val rating: Double?,
)

@Serializable
data class MangaBakaCover(
    val x250: MangaBakaScaledCover,
)

@Serializable
data class MangaBakaScaledCover(
    val x1: String?,
)
