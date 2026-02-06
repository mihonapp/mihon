package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.Serializable

@Serializable
data class MALSearchResult(
    val data: List<MALSearchResultNode>,
    val paging: MALSearchPaging,
)

@Serializable
data class MALSearchResultNode(
    val node: MALManga,
)

@Serializable
data class MALSearchPaging(
    val next: String?,
)
