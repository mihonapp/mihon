package mihon.domain.manga.local.repository

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow

interface LocalMangaRepository {

    suspend fun getAllSManga(): List<SManga>

    suspend fun getAllSMangaOrderedByTitleAsc(): List<SManga>

    suspend fun getAllSMangaOrderedByTitleDesc(): List<SManga>

    suspend fun getAllSMangaOrderedByDateAsc(): List<SManga>

    suspend fun getAllSMangaOrderedByDateDesc(): List<SManga>

    fun getAllSMangaAsFlow(): Flow<List<SManga>>

    suspend fun getSMangaByUrl(url: String): SManga?

    suspend fun updateThumbnailUrl(url: String, thumbnailUrl: String?)

    suspend fun insertOrReplaceSManga(manga: SManga)

    suspend fun deleteSManga(manga: List<SManga>)
}
