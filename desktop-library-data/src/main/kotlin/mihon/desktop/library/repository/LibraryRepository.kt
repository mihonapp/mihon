package mihon.desktop.library.repository

import kotlinx.coroutines.flow.Flow
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.TrackingRecord

interface LibraryRepository {
    fun observeLibrary(categoryId: Long? = null): Flow<List<LibraryManga>>
    fun observeManga(id: Long): Flow<MangaDetails?>
    fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>>
    fun observeCategories(): Flow<List<CategoryRecord>>
    fun observeHistory(query: String = ""): Flow<List<HistoryWithDetails>>
    fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>>
    fun librarySnapshot(categoryId: Long? = null): List<LibraryManga>
    fun mangaSnapshot(id: Long): MangaDetails?
    fun chapterSnapshot(mangaId: Long): List<LibraryChapter>
    fun categoriesSnapshot(): List<CategoryRecord>
    fun historySnapshot(query: String = ""): List<HistoryWithDetails>
    fun trackingSnapshot(mangaId: Long): List<TrackingRecord>
    fun latestImportReport(): ImportReport?
}
