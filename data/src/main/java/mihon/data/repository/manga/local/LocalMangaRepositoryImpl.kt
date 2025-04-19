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

    override suspend fun getSMangaOrderedByTitleAsc(urls: List<String>): List<SManga> {
        return handler.awaitList { local_mangaQueries.getMangaOrderedByTitleAsc(urls, LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getSMangaOrderedByTitleDesc(urls: List<String>): List<SManga> {
        return handler.awaitList { local_mangaQueries.getMangaOrderedByTitleDesc(urls, LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getSMangaOrderedByDateAsc(urls: List<String>): List<SManga> {
        return handler.awaitList { local_mangaQueries.getMangaOrderedByDateAsc(urls, LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getSMangaOrderedByDateDesc(urls: List<String>): List<SManga> {
        return handler.awaitList { local_mangaQueries.getMangaOrderedByDateDesc(urls, LocalSMangaMapper::mapSManga) }
    }

    override fun getAllSMangaAsFlow(): Flow<List<SManga>> {
        return handler.subscribeToList { local_mangaQueries.getAllManga(LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getSMangaByUrl(url: String): SManga? {
        return handler.awaitOneOrNull { local_mangaQueries.getMangaByUrl(url, LocalSMangaMapper::mapSManga) }
    }

    override suspend fun getFilteredLocalSourceUrls(
        excludedAuthors: Collection<String>,
        excludedArtists: Collection<String>,
        excludedGenres: Collection<String>,
        excludedStatuses: Collection<Long>,
        includedAuthors: Collection<String>,
        includedArtists: Collection<String>,
        includedGenres: Collection<String>,
        includedStatuses: Collection<Long>,
    ): List<String> {
        return handler.awaitList(inTransaction = true) {
            local_mangaQueries.getFilteredUrls(
                excludedAuthors = excludedAuthors,
                excludedArtists = excludedArtists,
                excludedGenres = excludedGenres,
                excludedStatuses = excludedStatuses,
                includedAuthors = includedAuthors,
                noFilterAuthor = includedAuthors.isEmpty(),
                includedArtists = includedArtists,
                noFilterArtist = includedArtists.isEmpty(),
                includedGenres = includedGenres,
                noFilterGenre = includedGenres.isEmpty(),
                includedStatuses = includedStatuses,
                noFilterStatus = includedStatuses.isEmpty(),
            )
        }
    }

    override suspend fun getLocalSourceFilterValues(): Triple<List<String>, List<String>, List<String>> {
        val authors = mutableListOf<String>()
        val artists = mutableListOf<String>()
        val genres = mutableListOf<String>()
        handler.awaitList {
            local_mangaQueries.getFilterValues()
        }.forEach { result ->
            result.author_item.takeUnless { it.isBlank() }?.let { authors.add(it) }
            result.artist_item.takeUnless { it.isBlank() }?.let { artists.add(it) }
            result.genre_item.takeUnless { it.isBlank() }?.let { genres.add(it) }
        }
        return Triple(authors, artists, genres)
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
