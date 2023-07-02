package tachiyomi.domain.libraryUpdateErrorMessage.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository

class GetLibraryUpdateErrorMessages(
    private val libraryUpdateErrorMessageRepository: LibraryUpdateErrorMessageRepository,
) {

    fun subscribe(): Flow<List<LibraryUpdateErrorMessage>> {
        return libraryUpdateErrorMessageRepository.getAllAsFlow()
    }

    suspend fun await(): List<LibraryUpdateErrorMessage> {
        return libraryUpdateErrorMessageRepository.getAll()
    }
}
