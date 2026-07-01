package tachiyomi.domain.updates.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.MangaUpdateError
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga

interface MangaUpdateErrorRepository {

    suspend fun getAll(): List<MangaUpdateError>

    suspend fun getByMangaId(mangaId: Long): MangaUpdateError?

    fun subscribeAll(): Flow<List<MangaUpdateError>>

    fun subscribeCount(): Flow<Long>

    fun subscribeWithManga(): Flow<List<MangaUpdateErrorWithManga>>

    suspend fun getCount(): Long

    suspend fun insert(mangaId: Long, errorMessage: String?, timestamp: Long)

    suspend fun delete(mangaId: Long)

    suspend fun deleteAll()

    suspend fun deleteNonFavorites()
}
