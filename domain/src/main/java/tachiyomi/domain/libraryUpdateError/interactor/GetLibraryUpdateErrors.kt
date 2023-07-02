package tachiyomi.domain.libraryUpdateError.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

class GetLibraryUpdateErrors(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository,
) {

    fun subscribe(): Flow<List<LibraryUpdateError>> {
        return libraryUpdateErrorRepository.getAllAsFlow()
    }

    suspend fun await(): List<LibraryUpdateError> {
        return libraryUpdateErrorRepository.getAll()
    }
}
