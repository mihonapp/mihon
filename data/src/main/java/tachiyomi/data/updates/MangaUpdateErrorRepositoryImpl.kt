package tachiyomi.data.updates

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.updates.model.MangaUpdateError
import tachiyomi.domain.updates.repository.MangaUpdateErrorRepository

class MangaUpdateErrorRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
) : MangaUpdateErrorRepository {

    override suspend fun getAll(): List<MangaUpdateError> {
        return databaseHandler.awaitList {
            manga_update_errorsQueries.getAll(::mapMangaUpdateError)
        }
    }

    override suspend fun getByMangaId(mangaId: Long): MangaUpdateError? {
        return databaseHandler.awaitOneOrNull {
            manga_update_errorsQueries.getByMangaId(mangaId, ::mapMangaUpdateError)
        }
    }

    override fun subscribeAll(): Flow<List<MangaUpdateError>> {
        return databaseHandler.subscribeToList {
            manga_update_errorsQueries.getAll(::mapMangaUpdateError)
        }
    }

    override suspend fun getCount(): Long {
        return databaseHandler.awaitOne {
            manga_update_errorsQueries.getCount()
        }
    }

    override suspend fun insert(mangaId: Long, errorMessage: String?, timestamp: Long) {
        databaseHandler.await {
            manga_update_errorsQueries.insert(mangaId, errorMessage, timestamp)
        }
    }

    override suspend fun delete(mangaId: Long) {
        databaseHandler.await {
            manga_update_errorsQueries.delete(mangaId)
        }
    }

    override suspend fun deleteAll() {
        databaseHandler.await {
            manga_update_errorsQueries.deleteAll()
        }
    }

    override suspend fun deleteNonFavorites() {
        databaseHandler.await {
            manga_update_errorsQueries.deleteNonFavorites()
        }
    }

    private fun mapMangaUpdateError(
        mangaId: Long,
        errorMessage: String?,
        timestamp: Long,
    ): MangaUpdateError = MangaUpdateError(
        mangaId = mangaId,
        errorMessage = errorMessage,
        timestamp = timestamp,
    )
}
