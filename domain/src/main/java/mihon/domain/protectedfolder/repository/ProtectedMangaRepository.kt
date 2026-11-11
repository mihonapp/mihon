package mihon.domain.protectedfolder.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

interface ProtectedMangaRepository {

    suspend fun getProtectedMangaIds(): Set<Long>

    fun getProtectedMangaIdsAsFlow(): Flow<Set<Long>>

    /**
     * Every entry of the protected folder, including those no longer in the library.
     */
    suspend fun getProtectedManga(): List<Manga>

    suspend fun getProtectedLibraryManga(): List<LibraryManga>

    fun getProtectedLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    suspend fun setProtected(mangaIds: List<Long>, protected: Boolean)

    /**
     * Returns every protected entry to the regular library.
     */
    suspend fun unprotectAll()

    /**
     * Deletes every protected entry with its chapters, history, tracks and categories, all together or not at all.
     */
    suspend fun deleteProtectedManga()
}
