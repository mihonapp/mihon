package tachiyomi.domain.libraryUpdateError.interactor

import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository

class InsertLibraryUpdateErrors(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository,
) {
    suspend fun upsert(libraryUpdateError: LibraryUpdateError) {
        return libraryUpdateErrorRepository.upsert(libraryUpdateError)
    }

    suspend fun insert(libraryUpdateError: LibraryUpdateError) {
        return libraryUpdateErrorRepository.insert(libraryUpdateError)
    }

    suspend fun insertAll(libraryUpdateErrors: List<LibraryUpdateError>) {
        return libraryUpdateErrorRepository.insertAll(libraryUpdateErrors)
    }
}
