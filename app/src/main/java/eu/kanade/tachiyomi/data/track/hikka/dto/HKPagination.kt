package eu.kanade.tachiyomi.data.track.hikka.dto

import kotlinx.serialization.Serializable

@Serializable
data class HKPagination(
    val total: Int,
    val pages: Int,
    val page: Int
)
