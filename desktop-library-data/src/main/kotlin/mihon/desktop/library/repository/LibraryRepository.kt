package mihon.desktop.library.repository

import kotlinx.coroutines.flow.Flow
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails

interface LibraryRepository {
    fun observeLibrary(): Flow<List<LibraryManga>>
    fun observeManga(id: Long): Flow<MangaDetails?>
    fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>>
    fun librarySnapshot(): List<LibraryManga>
    fun mangaSnapshot(id: Long): MangaDetails?
    fun chapterSnapshot(mangaId: Long): List<LibraryChapter>
    fun latestImportReport(): ImportReport?
}
