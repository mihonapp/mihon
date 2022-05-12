package eu.kanade.domain.manga.repository

import eu.kanade.domain.manga.model.Manga
import kotlinx.coroutines.flow.Flow

interface MangaRepository {

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean
}
