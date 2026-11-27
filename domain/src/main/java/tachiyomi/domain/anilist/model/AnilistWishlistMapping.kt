package tachiyomi.domain.anilist.model

import kotlinx.serialization.Serializable

@Serializable
data class AnilistWishlistMapping(
    val anilistMediaId: Long,
    val mangaId: Long,
)

data class AnilistWishlistMappingWithMangaIdentity(
    val anilistMediaId: Long,
    val sourceId: Long?,
    val url: String?,
)
