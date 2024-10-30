package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.Serializable

@Serializable
data class MALUserSearchResult(
    val data: List<MALUserSearchItem>,
    val paging: MALUserSearchPaging,
)

@Serializable
data class MALUserSearchItem(
    val node: MALUserSearchItemNode,
)

@Serializable
data class MALUserSearchPaging(
    val next: String?,
)

@Serializable
data class MALUserSearchItemNode(
    val id: Int,
    val title: String,
)
