package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority

class MangaRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
) : MangaRepository {

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return databaseHandler.subscribeToList { mangasQueries.getFavoriteBySourceId(sourceId, mangaMapper) }
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            databaseHandler.await { mangasQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }
}
