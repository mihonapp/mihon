package tachiyomi.domain.anilist.repository

import tachiyomi.domain.anilist.model.AnilistWishlistMapping

interface AnilistWishlistRepository {

    suspend fun getAll(): List<AnilistWishlistMapping>

    suspend fun getByAniListMediaId(anilistMediaId: Long): AnilistWishlistMapping?

    suspend fun getByMangaId(mangaId: Long): AnilistWishlistMapping?

    suspend fun upsert(mapping: AnilistWishlistMapping)

    suspend fun delete(anilistMediaId: Long)
}
