package mihon.desktop.download

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.HistoryWithDetails
import mihon.desktop.library.model.ImportReport
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DownloadCacheCleanerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `calculateDownloadSize correctly sums sizes in directory`() {
        val downloads = tempDir.resolve("downloads")
        Files.createDirectories(downloads.resolve("manga1/ch1"))
        Files.writeString(downloads.resolve("manga1/ch1/001.jpg"), "12345")
        Files.writeString(downloads.resolve("manga1/ch1/002.jpg"), "12345")

        val size = DownloadCacheCleaner.calculateDownloadSize(downloads)
        size shouldBe 10L
    }

    @Test
    fun `clearImageDiskCache removes all files and calculates freed bytes`() {
        val cache = tempDir.resolve("cache")
        Files.createDirectories(cache.resolve("sub"))
        Files.writeString(cache.resolve("sub/img1.png"), "abcdef")
        Files.writeString(cache.resolve("img2.png"), "1234")

        val freed = DownloadCacheCleaner.clearImageDiskCache(cache)
        freed shouldBe 10L
        Files.exists(cache.resolve("sub/img1.png")) shouldBe false
        Files.exists(cache.resolve("img2.png")) shouldBe false
    }

    @Test
    fun `deleteReadChapters removes only read chapters`() {
        val downloads = tempDir.resolve("downloads")
        val diskProvider = DownloadDiskProvider(downloads)

        val sourceId = 100L
        val mangaTitle = "Test Manga"
        val ch1Dir = diskProvider.getChapterDir(sourceId, mangaTitle, "Chapter 1")
        val ch2Dir = diskProvider.getChapterDir(sourceId, mangaTitle, "Chapter 2")
        Files.createDirectories(ch1Dir)
        Files.createDirectories(ch2Dir)
        Files.writeString(ch1Dir.resolve("page1.jpg"), "read_page_bytes_123")
        Files.writeString(ch2Dir.resolve("page1.jpg"), "unread_page_bytes_456")

        val manga = MangaRecord(
            id = 1L,
            sourceId = sourceId,
            url = "/manga/1",
            title = mangaTitle,
        )

        val ch1 = LibraryChapter(
            id = 101L,
            mangaId = 1L,
            url = "/ch/1",
            name = "Chapter 1",
            scanlator = null,
            read = true, // Read!
            bookmark = false,
            lastPageRead = 1L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = 1.0,
            sourceOrder = 1L,
            lastModifiedAt = 0L,
            version = 1L,
            memoJson = "{}",
        )
        val ch2 = ch1.copy(
            id = 102L,
            url = "/ch/2",
            name = "Chapter 2",
            read = false, // Unread!
            chapterNumber = 2.0,
            sourceOrder = 2L,
        )

        val fakeRepo = object : LibraryRepository {
            override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = emptyFlow()
            override fun observeManga(id: Long): Flow<MangaDetails?> = emptyFlow()
            override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = emptyFlow()
            override fun observeCategories(): Flow<List<CategoryRecord>> = emptyFlow()
            override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = emptyFlow()
            override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = emptyFlow()
            override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = emptyList()
            override fun mangaSnapshot(id: Long): MangaDetails? = null
            override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = listOf(ch1, ch2)
            override fun categoriesSnapshot(): List<CategoryRecord> = emptyList()
            override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()
            override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = emptyList()
            override fun latestImportReport(): ImportReport? = null
            override fun allMangaSnapshot(): List<MangaRecord> = listOf(manga)
            override fun allChaptersSnapshot(): List<ChapterRecord> = emptyList()
            override fun allCategoriesSnapshot(): List<CategoryRecord> = emptyList()
            override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = emptyMap()
            override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()
            override fun allTrackingSnapshot(): List<TrackingRecord> = emptyList()
            override fun allSourcesSnapshot(): List<SourceRecord> = emptyList()
            override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()
            override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()
            override fun checkIntegrity(): List<String> = emptyList()
        }

        val cleaner = DownloadCacheCleaner(fakeRepo, diskProvider)
        val report = cleaner.deleteReadChapters()

        report.deletedChaptersCount shouldBe 1
        Files.exists(ch1Dir) shouldBe false
        Files.exists(ch2Dir) shouldBe true
    }

    @Test
    fun `deleteReadChapters uses coordinated deletion when available`() {
        val downloads = tempDir.resolve("coordinated-downloads")
        val diskProvider = DownloadDiskProvider(downloads)
        val manga = MangaRecord(id = 1L, sourceId = 100L, url = "/manga/1", title = "Test Manga")
        val chapter = LibraryChapter(
            id = 101L,
            mangaId = manga.id,
            url = "/ch/1",
            name = "Chapter 1",
            scanlator = null,
            read = true,
            bookmark = false,
            lastPageRead = 1L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = 1.0,
            sourceOrder = 1L,
            lastModifiedAt = 0L,
            version = 1L,
            memoJson = "{}",
        )
        val chapterDir = diskProvider.getChapterDir(manga.sourceId, manga.title, chapter.name)
        Files.createDirectories(chapterDir)
        Files.writeString(chapterDir.resolve("001.jpg"), "bytes")
        val repository = fakeRepository(manga, listOf(chapter))
        var coordinatedDeletes = 0
        val cleaner = DownloadCacheCleaner(repository, diskProvider) { deletedManga, deletedChapter ->
            deletedManga.id shouldBe manga.id
            deletedChapter.id shouldBe chapter.id
            coordinatedDeletes++
            diskProvider.deleteChapter(deletedManga.sourceId, deletedManga.title, deletedChapter.name)
        }

        cleaner.deleteReadChapters().deletedChaptersCount shouldBe 1
        coordinatedDeletes shouldBe 1
    }

    private fun fakeRepository(manga: MangaRecord, chapters: List<LibraryChapter>): LibraryRepository =
        object : LibraryRepository {
            override fun observeLibrary(categoryId: Long?): Flow<List<LibraryManga>> = emptyFlow()
            override fun observeManga(id: Long): Flow<MangaDetails?> = emptyFlow()
            override fun observeChapters(mangaId: Long): Flow<List<LibraryChapter>> = emptyFlow()
            override fun observeCategories(): Flow<List<CategoryRecord>> = emptyFlow()
            override fun observeHistory(query: String): Flow<List<HistoryWithDetails>> = emptyFlow()
            override fun observeTracking(mangaId: Long): Flow<List<TrackingRecord>> = emptyFlow()
            override fun librarySnapshot(categoryId: Long?): List<LibraryManga> = emptyList()
            override fun mangaSnapshot(id: Long): MangaDetails? = null
            override fun chapterSnapshot(mangaId: Long): List<LibraryChapter> = chapters
            override fun categoriesSnapshot(): List<CategoryRecord> = emptyList()
            override fun historySnapshot(query: String): List<HistoryWithDetails> = emptyList()
            override fun trackingSnapshot(mangaId: Long): List<TrackingRecord> = emptyList()
            override fun latestImportReport(): ImportReport? = null
            override fun allMangaSnapshot(): List<MangaRecord> = listOf(manga)
            override fun allChaptersSnapshot(): List<ChapterRecord> = emptyList()
            override fun allCategoriesSnapshot(): List<CategoryRecord> = emptyList()
            override fun mangaCategoryLinksSnapshot(): Map<Long, List<Long>> = emptyMap()
            override fun allHistorySnapshot(): List<HistoryRecord> = emptyList()
            override fun allTrackingSnapshot(): List<TrackingRecord> = emptyList()
            override fun allSourcesSnapshot(): List<SourceRecord> = emptyList()
            override fun allPreferenceSnapshots(): List<PreferenceSnapshotRecord> = emptyList()
            override fun allSourcePreferenceSnapshots(): List<SourcePreferenceSnapshotRecord> = emptyList()
            override fun checkIntegrity(): List<String> = emptyList()
        }
}
