package mihon.data.protectedfolder

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository
import tachiyomi.data.Database
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.subscribeToList
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ProtectedMangaRepositoryImpl(
    private val database: Database,
) : ProtectedMangaRepository {

    override suspend fun getProtectedMangaIds(): Set<Long> {
        return database.protected_mangaQueries
            .getProtectedMangaIds()
            .awaitAsList()
            .toSet()
    }

    override fun getProtectedMangaIdsAsFlow(): Flow<Set<Long>> {
        return database.protected_mangaQueries
            .getProtectedMangaIds()
            .subscribeToList()
            .map { it.toSet() }
    }

    override suspend fun getProtectedManga(): List<Manga> {
        return database.protected_mangaQueries
            .getProtectedManga(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getProtectedLibraryManga(): List<LibraryManga> {
        return database.libraryViewQueries
            .protectedLibrary(MangaMapper::mapLibraryManga)
            .awaitAsList()
    }

    override fun getProtectedLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return database.libraryViewQueries
            .protectedLibrary(MangaMapper::mapLibraryManga)
            .subscribeToList()
    }

    override suspend fun setProtected(mangaIds: List<Long>, protected: Boolean) {
        if (protected) {
            database.transaction {
                mangaIds.forEach { database.protected_mangaQueries.insert(it) }
            }
        } else {
            database.protected_mangaQueries.delete(mangaIds)
        }
    }

    override suspend fun unprotectAll() {
        database.protected_mangaQueries.deleteAll()
    }

    override suspend fun deleteProtectedManga() {
        database.protected_mangaQueries.deleteProtectedManga()
    }
}
