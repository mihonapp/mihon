package tachiyomi.domain.libraryUpdateErrorMessage.interactor

import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository

class InsertLibraryUpdateErrorMessages(
    private val libraryUpdateErrorMessageRepository: LibraryUpdateErrorMessageRepository,
) {
    suspend fun get(message: String): Long? {
        return libraryUpdateErrorMessageRepository.get(message)
    }

    suspend fun insert(libraryUpdateErrorMessage: LibraryUpdateErrorMessage): Long {
        return libraryUpdateErrorMessageRepository.insert(libraryUpdateErrorMessage)
    }

    suspend fun insertAll(libraryUpdateErrorMessages: List<LibraryUpdateErrorMessage>): List<Pair<Long, String>> {
        return libraryUpdateErrorMessageRepository.insertAll(libraryUpdateErrorMessages)
    }
}
