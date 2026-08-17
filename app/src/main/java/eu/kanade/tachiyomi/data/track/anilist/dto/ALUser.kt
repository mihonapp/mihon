package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.type.ScoreFormat

@Serializable
data class ALUser(
    val id: Int,
    val name: String,
    val scoreFormat: String,
)

fun AniListGetCurrentUserQuery.Viewer.toALUser(): ALUser {
    return ALUser(
        id = id,
        name = name,
        scoreFormat = mediaListOptions?.scoreFormat?.rawValue ?: ScoreFormat.POINT_10_DECIMAL.rawValue,
    )
}
