package eu.kanade.domain.manga.repository

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun subscribeMangaById(id: Long): Flow<Manga>

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getFavorites(): List<Manga>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(title: String, sourceId: Long): Manga?

    suspend fun resetViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateAll(values: List<MangaUpdate>): Boolean
}
