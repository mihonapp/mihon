package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow

class MangaRepositoryImpl(
    private val databaseHandler: DatabaseHandler
) : MangaRepository {

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return databaseHandler.subscribeToList { mangasQueries.getFavoriteBySourceId(sourceId, mangaMapper) }
    }
}
