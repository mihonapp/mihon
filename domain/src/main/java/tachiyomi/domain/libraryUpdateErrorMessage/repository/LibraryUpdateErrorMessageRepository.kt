package tachiyomi.domain.libraryUpdateErrorMessage.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage

interface LibraryUpdateErrorMessageRepository {

    suspend fun getAll(): List<LibraryUpdateErrorMessage>

    fun getAllAsFlow(): Flow<List<LibraryUpdateErrorMessage>>

    suspend fun deleteAll()

    suspend fun get(message: String): Long?

    suspend fun insert(libraryUpdateErrorMessage: LibraryUpdateErrorMessage): Long

    suspend fun insertAll(libraryUpdateErrorMessages: List<LibraryUpdateErrorMessage>): List<Pair<Long, String>>
}
