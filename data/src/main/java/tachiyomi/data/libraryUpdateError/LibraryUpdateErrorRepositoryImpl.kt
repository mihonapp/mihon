package tachiyomi.data.libraryUpdateError

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

class LibraryUpdateErrorRepositoryImpl(
    private val handler: DatabaseHandler,
) : LibraryUpdateErrorRepository {

    override suspend fun getAll(): List<LibraryUpdateError> {
        return handler.awaitList {
            libraryUpdateErrorQueries.getAllErrors(
                libraryUpdateErrorMapper,
            )
        }
    }

    override fun getAllAsFlow(): Flow<List<LibraryUpdateError>> {
        return handler.subscribeToList {
            libraryUpdateErrorQueries.getAllErrors(
                libraryUpdateErrorMapper,
            )
        }
    }

    override suspend fun deleteAll() {
        return handler.await { libraryUpdateErrorQueries.deleteAllErrors() }
    }

    override suspend fun delete(errorId: Long) {
        return handler.await {
            libraryUpdateErrorQueries.deleteError(
                _id = errorId,
            )
        }
    }

    override suspend fun deleteMangaError(mangaId: Long) {
        return handler.await {
            libraryUpdateErrorQueries.deleteMangaError(
                mangaId = mangaId,
            )
        }
    }

    override suspend fun cleanUnrelevantMangaErrors() {
        return handler.await {
            libraryUpdateErrorQueries.cleanUnrelevantMangaErrors()
        }
    }

    override suspend fun upsert(libraryUpdateError: LibraryUpdateError) {
        return handler.await(inTransaction = true) {
            libraryUpdateErrorQueries.upsert(
                mangaId = libraryUpdateError.mangaId,
                messageId = libraryUpdateError.messageId,
            )
        }
    }

    override suspend fun insert(libraryUpdateError: LibraryUpdateError) {
        return handler.await(inTransaction = true) {
            libraryUpdateErrorQueries.insert(
                mangaId = libraryUpdateError.mangaId,
                messageId = libraryUpdateError.messageId,
            )
        }
    }

    override suspend fun insertAll(libraryUpdateErrors: List<LibraryUpdateError>) {
        return handler.await(inTransaction = true) {
            libraryUpdateErrors.forEach {
                libraryUpdateErrorQueries.insert(
                    mangaId = it.mangaId,
                    messageId = it.messageId,
                )
            }
        }
    }
}
