package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.source.model.MangaInfo
import java.util.Date

class UpdateManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitUpdateFromSource(
        localManga: Manga,
        remoteManga: MangaInfo,
        manualFetch: Boolean,
        coverCache: CoverCache,
    ): Boolean {
        // if the manga isn't a favorite, set its title from source and update in db
        val title = if (!localManga.favorite) remoteManga.title else null

        // Never refresh covers if the url is empty to avoid "losing" existing covers
        val updateCover = remoteManga.cover.isNotEmpty() && (manualFetch || localManga.thumbnailUrl != remoteManga.cover)
        val coverLastModified = if (updateCover) {
            when {
                localManga.isLocal() -> Date().time
                localManga.hasCustomCover(coverCache) -> {
                    coverCache.deleteFromCache(localManga.toDbManga(), false)
                    null
                }
                else -> {
                    coverCache.deleteFromCache(localManga.toDbManga(), false)
                    Date().time
                }
            }
        } else null

        return mangaRepository.update(
            MangaUpdate(
                id = localManga.id,
                title = title?.takeIf { it.isNotEmpty() },
                coverLastModified = coverLastModified,
                author = remoteManga.author,
                artist = remoteManga.artist,
                description = remoteManga.description,
                genre = remoteManga.genres,
                thumbnailUrl = remoteManga.cover.takeIf { it.isNotEmpty() },
                status = remoteManga.status.toLong(),
                initialized = true,
            ),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, lastUpdate = Date().time))
    }
}
