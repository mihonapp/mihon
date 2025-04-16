package mihon.domain.manga.local.repository

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow

interface LocalMangaRepository {

    suspend fun getAllSManga(): List<SManga>

    suspend fun getSMangaOrderedByTitleAsc(urls: List<String>): List<SManga>

    suspend fun getSMangaOrderedByTitleDesc(urls: List<String>): List<SManga>

    suspend fun getSMangaOrderedByDateAsc(urls: List<String>): List<SManga>

    suspend fun getSMangaOrderedByDateDesc(urls: List<String>): List<SManga>

    fun getAllSMangaAsFlow(): Flow<List<SManga>>

    suspend fun getSMangaByUrl(url: String): SManga?

    suspend fun getLocalSourceFilterValues(): Triple<List<String>, List<String>, List<String>>

    suspend fun getFilteredLocalSourceUrls(
        excludedAuthors: Collection<String>,
        excludedArtists: Collection<String>,
        excludedGenres: Collection<String>,
        excludedStatuses: Collection<Long>,
        includedAuthors: Collection<String>,
        includedArtists: Collection<String>,
        includedGenres: Collection<String>,
        includedStatuses: Collection<Long>,
    ): List<String>

    suspend fun updateThumbnailUrl(url: String, thumbnailUrl: String?)

    suspend fun insertOrReplaceSManga(manga: SManga)

    suspend fun deleteSManga(manga: List<SManga>)
}
