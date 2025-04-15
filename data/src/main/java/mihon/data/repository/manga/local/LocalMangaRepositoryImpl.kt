package mihon.data.repository.manga.local

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import mihon.domain.manga.local.repository.LocalMangaRepository
import tachiyomi.data.DatabaseHandler

class LocalMangaRepositoryImpl(
    private val handler: DatabaseHandler,
) : LocalMangaRepository {

    override suspend fun getAllSManga(): List<SManga> {
        return handler.awaitList { local_mangaQueries.getAllManga(LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getAllSMangaOrderedByTitleAsc(): List<SManga> {
        return handler.awaitList { local_mangaQueries.getAllMangaOrderedByTitleAsc(LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getAllSMangaOrderedByTitleDesc(): List<SManga> {
        return handler.awaitList { local_mangaQueries.getAllMangaOrderedByTitleDesc(LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getAllSMangaOrderedByDateAsc(): List<SManga> {
        return handler.awaitList { local_mangaQueries.getAllMangaOrderedByDateAsc(LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getAllSMangaOrderedByDateDesc(): List<SManga> {
        return handler.awaitList { local_mangaQueries.getAllMangaOrderedByDateDesc(LocalSMangaMapper::mapSManga) }
    }

    override fun getAllSMangaAsFlow(): Flow<List<SManga>> {
        return handler.subscribeToList { local_mangaQueries.getAllManga(LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getSMangaByUrl(url: String): SManga? {
        return handler.awaitOneOrNull { local_mangaQueries.getMangaByUrl(url, LocalSMangaMapper::mapSManga) }
    }

    override suspend fun updateThumbnailUrl(url: String, thumbnailUrl: String?) {
        return handler.await(inTransaction = true) {
            local_mangaQueries.updateThumbnailUrl(
                url = url,
                thumbnailUrl = thumbnailUrl,
            )
        }
    }

    override suspend fun insertOrReplaceSManga(manga: SManga) {
        return handler.await(inTransaction = true) {
            local_mangaQueries.insertOrReplace(
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.getGenres(),
                title = manga.title,
                status = manga.status.toLong(),
                thumbnailUrl = manga.thumbnail_url,
                dirLastModified = manga.dir_last_modified!!,
            )
        }
    }

    override suspend fun deleteSManga(manga: List<SManga>) {
        return handler.await(inTransaction = true) {
            manga.map {
                local_mangaQueries.deleteManga(
                    url = it.url,
                )
            }
        }
    }
}
