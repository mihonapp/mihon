package eu.kanade.domain.manga.repository

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun update(update: MangaUpdate): Boolean
}
