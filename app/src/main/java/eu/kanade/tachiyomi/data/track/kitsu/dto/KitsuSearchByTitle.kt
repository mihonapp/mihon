package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuSearchByTitleResult(
    val data: KitsuSearchByTitleData,
)

@Serializable
data class KitsuSearchByTitleData(
    val searchMangaByTitle: KitsuSearchNodes,
)

@Serializable
data class KitsuSearchNodes(
    val nodes: List<KitsuManga>,
)
