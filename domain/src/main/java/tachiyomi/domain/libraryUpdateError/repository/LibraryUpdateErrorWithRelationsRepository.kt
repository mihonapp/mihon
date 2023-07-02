package tachiyomi.domain.libraryUpdateError.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations

interface LibraryUpdateErrorWithRelationsRepository {

    fun subscribeAll(): Flow<List<LibraryUpdateErrorWithRelations>>
}
