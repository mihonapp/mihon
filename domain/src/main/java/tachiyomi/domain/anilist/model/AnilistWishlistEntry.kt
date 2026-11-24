package tachiyomi.domain.anilist.model

import kotlinx.serialization.Serializable

@Serializable
data class AnilistWishlistEntry(
    val mediaId: Long,
    val malId: Int?,
    val title: String,
    val englishTitle: String?,
    val romajiTitle: String?,
    val totalChapters: Int?,
    val coverUrl: String?,
)
