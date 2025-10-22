package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.DuplicateManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    suspend fun getMangaByIdWithChapterCount(id: Long): MangaWithChapterCount

    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>

    suspend fun getFavorites(): List<Manga>

    suspend fun getReadMangaNotInLibrary(): List<Manga>

    suspend fun getLibraryManga(): List<LibraryManga>

    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(id: Long, searchString: String): List<MangaWithChapterCount>

    suspend fun getAllDuplicateLibraryMangaAsFlow(fuzzy: Boolean = false): Flow<List<DuplicateManga>>

    suspend fun getHiddenDuplicates(manga: Manga): List<MangaWithChapterCount>

    suspend fun getHiddenDuplicatesAsFlow(manga: Manga): Flow<List<MangaWithChapterCount>>

    suspend fun getAllHiddenDuplicateManga(): Flow<List<DuplicateManga>>

    suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean

    suspend fun insertNetworkManga(manga: List<Manga>): List<Manga>
}
