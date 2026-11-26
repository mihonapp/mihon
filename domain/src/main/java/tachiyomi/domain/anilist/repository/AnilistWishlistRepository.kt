package tachiyomi.domain.anilist.repository

import tachiyomi.domain.anilist.model.AnilistWishlistMapping
import tachiyomi.domain.anilist.model.AnilistWishlistMappingWithMangaIdentity

interface AnilistWishlistRepository {

    sealed interface InsertResult {
        data object Inserted : InsertResult
        data object AlreadyMapped : InsertResult
        data object Conflict : InsertResult
        data object Invalid : InsertResult
    }

    suspend fun getAll(): List<AnilistWishlistMapping>

    suspend fun getAllWithMangaIdentity(): List<AnilistWishlistMappingWithMangaIdentity>

    suspend fun getByAniListMediaId(anilistMediaId: Long): AnilistWishlistMapping?

    suspend fun getByMangaId(mangaId: Long): AnilistWishlistMapping?

    suspend fun insertIfAbsent(mapping: AnilistWishlistMapping): InsertResult

    suspend fun upsert(mapping: AnilistWishlistMapping)

    suspend fun delete(anilistMediaId: Long)
}
