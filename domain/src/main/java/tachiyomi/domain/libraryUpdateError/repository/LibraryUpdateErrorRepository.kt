package tachiyomi.domain.libraryUpdateError.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError

interface LibraryUpdateErrorRepository {

    suspend fun getAll(): List<LibraryUpdateError>

    fun getAllAsFlow(): Flow<List<LibraryUpdateError>>

    suspend fun deleteAll()

    suspend fun delete(errorId: Long)

    suspend fun deleteMangaError(mangaId: Long)

    suspend fun upsert(libraryUpdateError: LibraryUpdateError)

    suspend fun insert(libraryUpdateError: LibraryUpdateError)

    suspend fun insertAll(libraryUpdateErrors: List<LibraryUpdateError>)
}
