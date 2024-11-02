package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(manga: Manga): Manga {
        val localManga = getManga(manga.url, manga.source)
        return when {
            localManga == null -> {
                val id = insertManga(manga)
                manga.copy(id = id!!)
            }
            !localManga.favorite -> {
                // if the manga isn't a favorite, update new info from source to db
                manga.updateManga(localManga.id)
                manga.copy(id = localManga.id)
            }
            else -> {
                localManga
            }
        }
    }

    private suspend fun getManga(url: String, sourceId: Long): Manga? {
        return mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertManga(manga: Manga): Long? {
        return mangaRepository.insert(manga)
    }

    private suspend fun Manga.updateManga(id: Long) {
        mangaRepository.update(
            toMangaUpdate()
                .copy(
                    id = id,
                    thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
                ),
        )
    }
}
