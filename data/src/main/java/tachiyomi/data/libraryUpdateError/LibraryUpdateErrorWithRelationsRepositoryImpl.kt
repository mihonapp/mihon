package tachiyomi.data.libraryUpdateError

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository

class LibraryUpdateErrorWithRelationsRepositoryImpl(
    private val handler: DatabaseHandler,
) : LibraryUpdateErrorWithRelationsRepository {

    override fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>> {
        return handler.subscribeToList {
            libraryUpdateErrorViewQueries.errors(
                libraryUpdateErrorWithRelationsMapper,
            )
        }
    }
}
