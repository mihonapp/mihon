package tachiyomi.data.libraryUpdateErrorMessage

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository

class LibraryUpdateErrorMessageRepositoryImpl(
    private val handler: DatabaseHandler,
) : LibraryUpdateErrorMessageRepository {

    override suspend fun getAll(): List<LibraryUpdateErrorMessage> {
        return handler.awaitList {
            libraryUpdateErrorMessageQueries.getAllErrorMessages(
                LibraryUpdateErrorMessageMapper,
            )
        }
    }

    override fun getAllAsFlow(): Flow<List<LibraryUpdateErrorMessage>> {
        return handler.subscribeToList {
            libraryUpdateErrorMessageQueries.getAllErrorMessages(
                LibraryUpdateErrorMessageMapper,
            )
        }
    }

    override suspend fun deleteAll() {
        return handler.await { libraryUpdateErrorMessageQueries.deleteAllErrorMessages() }
    }

    override suspend fun insert(libraryUpdateErrorMessage: LibraryUpdateErrorMessage): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            libraryUpdateErrorMessageQueries.insert(libraryUpdateErrorMessage.message)
            libraryUpdateErrorMessageQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(libraryUpdateErrorMessages: List<LibraryUpdateErrorMessage>): List<Pair<Long, String>> {
        return handler.await(inTransaction = true) {
            libraryUpdateErrorMessages.map {
                libraryUpdateErrorMessageQueries.insert(it.message)
                libraryUpdateErrorMessageQueries.selectLastInsertedRowId().executeAsOne() to it.message
            }
        }
    }
}
