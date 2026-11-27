package tachiyomi.data.anilist

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.Database
import tachiyomi.domain.anilist.model.AnilistWishlistMapping
import tachiyomi.domain.anilist.model.AnilistWishlistMappingWithMangaIdentity
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository.InsertResult

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnilistWishlistRepositoryImpl(
    private val database: Database,
) : AnilistWishlistRepository {

    override suspend fun getAll(): List<AnilistWishlistMapping> {
        return database.anilist_wishlistQueries
            .getAll { anilistMediaId, mangaId ->
                AnilistWishlistMapping(anilistMediaId, mangaId)
            }
            .awaitAsList()
    }

    override suspend fun getAllWithMangaIdentity(): List<AnilistWishlistMappingWithMangaIdentity> {
        return database.anilist_wishlistQueries
            .getAllWithMangaIdentity { anilistMediaId, sourceId, url ->
                AnilistWishlistMappingWithMangaIdentity(anilistMediaId, sourceId, url)
            }
            .awaitAsList()
    }

    override suspend fun getByAniListMediaId(anilistMediaId: Long): AnilistWishlistMapping? {
        return database.anilist_wishlistQueries
            .getByAniListMediaId(anilistMediaId) { mediaId, mangaId ->
                AnilistWishlistMapping(mediaId, mangaId)
            }
            .awaitAsOneOrNull()
    }

    override suspend fun getByMangaId(mangaId: Long): AnilistWishlistMapping? {
        return database.anilist_wishlistQueries
            .getByMangaId(mangaId) { mediaId, localMangaId ->
                AnilistWishlistMapping(mediaId, localMangaId)
            }
            .awaitAsOneOrNull()
    }

    override suspend fun insertIfAbsent(mapping: AnilistWishlistMapping): InsertResult {
        return database.transactionWithResult {
            if (mapping.anilistMediaId <= 0) return@transactionWithResult InsertResult.Invalid
            val insertedMediaId = database.anilist_wishlistQueries
                .insertIfAbsent(
                    anilistMediaId = mapping.anilistMediaId,
                    mangaId = mapping.mangaId,
                )
                .awaitAsOneOrNull()

            if (insertedMediaId != null) {
                InsertResult.Inserted
            } else {
                val mangaExists = database.anilist_wishlistQueries
                    .getMangaForMapping(mapping.mangaId)
                    .awaitAsOneOrNull() != null
                if (!mangaExists) return@transactionWithResult InsertResult.Invalid

                val byMediaId = database.anilist_wishlistQueries
                    .getByAniListMediaId(mapping.anilistMediaId) { mediaId, mangaId ->
                        AnilistWishlistMapping(mediaId, mangaId)
                    }
                    .awaitAsOneOrNull()
                val byMangaId = database.anilist_wishlistQueries
                    .getByMangaId(mapping.mangaId) { mediaId, mangaId ->
                        AnilistWishlistMapping(mediaId, mangaId)
                    }
                    .awaitAsOneOrNull()

                if (byMediaId == mapping && byMangaId == mapping) {
                    InsertResult.AlreadyMapped
                } else {
                    InsertResult.Conflict
                }
            }
        }
    }

    override suspend fun upsert(mapping: AnilistWishlistMapping) {
        database.anilist_wishlistQueries.upsert(
            anilistMediaId = mapping.anilistMediaId,
            mangaId = mapping.mangaId,
        )
    }

    override suspend fun delete(anilistMediaId: Long) {
        database.anilist_wishlistQueries.delete(anilistMediaId)
    }
}
