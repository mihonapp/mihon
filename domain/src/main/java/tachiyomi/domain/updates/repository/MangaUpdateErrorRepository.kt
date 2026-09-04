package tachiyomi.domain.updates.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga

interface MangaUpdateErrorRepository {

    fun subscribeCount(): Flow<Long>

    fun subscribeWithManga(): Flow<List<MangaUpdateErrorWithManga>>

    suspend fun insert(mangaId: Long, errorMessage: String?, timestamp: Long)

    suspend fun delete(mangaId: Long)

    suspend fun deleteByMangaIds(mangaIds: List<Long>)

    suspend fun deleteAll()

    suspend fun deleteNonFavorites()
}
