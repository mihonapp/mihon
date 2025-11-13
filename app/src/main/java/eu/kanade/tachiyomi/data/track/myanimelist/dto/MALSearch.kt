package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.Serializable

@Serializable
data class MALSearchResult(
    val data: List<MALSearchResultNode>,
)

@Serializable
data class MALSearchResultNode(
    val node: MALSearchResultItem,
)

@Serializable
data class MALSearchResultItem(
    val id: Int,
)
