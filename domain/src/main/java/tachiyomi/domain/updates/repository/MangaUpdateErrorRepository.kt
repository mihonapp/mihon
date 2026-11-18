package tachiyomi.domain.updates.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.model.MangaUpdateErrorWithManga
import kotlin.time.Instant

interface MangaUpdateErrorRepository {

    fun subscribeCount(): Flow<Long>

    fun subscribeWithManga(): Flow<List<MangaUpdateErrorWithManga>>

    suspend fun insert(mangaId: Long, errorMessage: String?, timestamp: Instant)

    suspend fun delete(mangaId: Long)

    suspend fun deleteByMangaIds(mangaIds: List<Long>)

    suspend fun deleteAll()
}
