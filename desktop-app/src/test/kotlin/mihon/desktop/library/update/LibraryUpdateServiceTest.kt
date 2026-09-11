package mihon.desktop.library.update

import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.OnlineMangaSyncService
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.platform.DesktopNotificationService
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LibraryUpdateServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `empty library returns zero checked`() = runBlocking {
        val dbFile = tempDir.resolve("empty.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val sourceManager = DesktopSourceManager()
            val syncService = OnlineMangaSyncService(repo, sourceManager)
            val service = LibraryUpdateService(repo, syncService)

            val report = service.updateLibrary()
            assertEquals(0, report.totalMangaChecked)
            assertEquals(0, report.newChaptersTotal)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `skips completed and unread manga according to options`() = runBlocking {
        val dbFile = tempDir.resolve("filter.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val now = System.currentTimeMillis()
            // 1. Favorite manga, ongoing (status = 1)
            val ongoingId = repo.insertManga(
                MangaRecord(
                    id = 0L,
                    sourceId = 100L,
                    url = "/manga/ongoing",
                    title = "Ongoing Manga",
                    artist = "Artist",
                    author = "Author",
                    description = "Desc",
                    genreJson = "[]",
                    status = 1L,
                    thumbnailUrl = null,
                    favorite = true,
                    dateAdded = now,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                    initialized = true,
                ),
            )

            // 2. Favorite manga, completed (status = 2)
            repo.insertManga(
                MangaRecord(
                    id = 0L,
                    sourceId = 100L,
                    url = "/manga/completed",
                    title = "Completed Manga",
                    artist = "Artist",
                    author = "Author",
                    description = "Desc",
                    genreJson = "[]",
                    status = 2L,
                    thumbnailUrl = null,
                    favorite = true,
                    dateAdded = now,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                    initialized = true,
                ),
            )

            // 3. Non-favorite manga (status = 1)
            repo.insertManga(
                MangaRecord(
                    id = 0L,
                    sourceId = 100L,
                    url = "/manga/non-fav",
                    title = "Non Favorite Manga",
                    artist = "Artist",
                    author = "Author",
                    description = "Desc",
                    genreJson = "[]",
                    status = 1L,
                    thumbnailUrl = null,
                    favorite = false,
                    dateAdded = now,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                    initialized = true,
                ),
            )

            val source = MockSource(100L)
            val sourceManager = DesktopSourceManager()
            sourceManager.registerBuiltinSource(source)
            val syncService = OnlineMangaSyncService(repo, sourceManager)
            val service = LibraryUpdateService(repo, syncService)

            // skipCompleted = true: should check only ongoing manga (1 manga)
            val report = service.updateLibrary(
                options = LibraryUpdateOptions(skipCompleted = true),
                throttleDelayMs = 0L,
            )
            assertEquals(1, report.totalMangaChecked)
            assertEquals(ongoingId, report.results.first().mangaId)

            // Add an unread chapter to ongoing manga
            repo.insertChapter(
                ChapterRecord(
                    id = 0L,
                    mangaId = ongoingId,
                    url = "/chapter/1",
                    name = "Chapter 1",
                    scanlator = null,
                    read = false,
                    bookmark = false,
                    lastPageRead = 0L,
                    dateFetch = now,
                    dateUpload = now,
                    chapterNumber = 1.0,
                    sourceOrder = 0L,
                    lastModifiedAt = now,
                ),
            )

            // skipUnread = true: should now skip ongoing manga since it has an unread chapter
            val reportSkipUnread = service.updateLibrary(
                options = LibraryUpdateOptions(skipCompleted = true, skipUnread = true),
                throttleDelayMs = 0L,
            )
            assertEquals(0, reportSkipUnread.totalMangaChecked)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `detects new chapters and triggers notification`() = runBlocking {
        val dbFile = tempDir.resolve("sync.db")
        val repo = DesktopLibraryDatabaseFactory.open(dbFile)
        try {
            val now = System.currentTimeMillis()
            val mangaId = repo.insertManga(
                MangaRecord(
                    id = 0L,
                    sourceId = 200L,
                    url = "/manga/mock",
                    title = "Mock Manga",
                    artist = "Artist",
                    author = "Author",
                    description = "Desc",
                    genreJson = "[]",
                    status = 1L,
                    thumbnailUrl = null,
                    favorite = true,
                    dateAdded = now,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                    initialized = true,
                ),
            )

            // Existing chapter in database
            repo.insertChapter(
                ChapterRecord(
                    id = 0L,
                    mangaId = mangaId,
                    url = "/chapter/1",
                    name = "Chapter 1",
                    scanlator = null,
                    read = true,
                    bookmark = false,
                    lastPageRead = 0L,
                    dateFetch = now,
                    dateUpload = now,
                    chapterNumber = 1.0,
                    sourceOrder = 0L,
                    lastModifiedAt = now,
                ),
            )

            // Source returns Chapter 1 and newly released Chapter 2 & Chapter 3
            val source = MockSource(
                id = 200L,
                chapters = listOf(
                    SChapter(url = "/chapter/1", name = "Chapter 1", chapterNumber = 1f),
                    SChapter(url = "/chapter/2", name = "Chapter 2", chapterNumber = 2f),
                    SChapter(url = "/chapter/3", name = "Chapter 3", chapterNumber = 3f),
                ),
            )

            val sourceManager = DesktopSourceManager()
            sourceManager.registerBuiltinSource(source)
            val syncService = OnlineMangaSyncService(repo, sourceManager)

            val notificationService = DesktopNotificationService()
            val service = LibraryUpdateService(
                repository = repo,
                syncService = syncService,
                notificationService = notificationService,
            )

            val progressList = mutableListOf<LibraryUpdateProgress>()
            val report = service.updateLibrary(
                onProgress = { progressList.add(it) },
                throttleDelayMs = 0L,
            )

            assertEquals(1, report.totalMangaChecked)
            assertEquals(1, report.updatedMangaCount)
            assertEquals(2, report.newChaptersTotal) // Chapters 2 & 3
            assertEquals(1, progressList.size)
            assertEquals("Mock Manga", progressList.first().currentMangaTitle)

            // Notification should have been emitted
            val lastNotif = notificationService.notifications.value
            assertNotNull(lastNotif)
            assertTrue(lastNotif!!.message.contains("Found 2 new chapter(s)"))
        } finally {
            repo.close()
        }
    }

    @Test
    fun `applies included excluded and not-started filters including imported preferences`() = runBlocking {
        val repo = DesktopLibraryDatabaseFactory.open(tempDir.resolve("category-filter.db"))
        try {
            val now = System.currentTimeMillis()
            fun addManga(title: String, url: String): Long = repo.insertManga(
                MangaRecord(
                    sourceId = 300L,
                    url = url,
                    title = title,
                    status = 1L,
                    favorite = true,
                    dateAdded = now,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                    initialized = true,
                ),
            )

            val included = addManga("Included", "/included")
            val excluded = addManga("Excluded", "/excluded")
            val notStarted = addManga("Not Started", "/not-started")
            val includeCategory = repo.upsertCategory(CategoryRecord(name = "Include"))
            val excludeCategory = repo.upsertCategory(CategoryRecord(name = "Exclude"))
            repo.linkCategory(included, includeCategory)
            repo.linkCategory(excluded, includeCategory)
            repo.linkCategory(excluded, excludeCategory)
            repo.linkCategory(notStarted, includeCategory)

            fun addChapter(mangaId: Long, read: Boolean) {
                repo.insertChapter(
                    ChapterRecord(
                        mangaId = mangaId,
                        url = "/chapter-$mangaId",
                        name = "Chapter 1",
                        read = read,
                        dateFetch = now,
                        dateUpload = now,
                        chapterNumber = 1.0,
                        lastModifiedAt = now,
                    ),
                )
            }
            addChapter(included, read = true)
            addChapter(excluded, read = true)
            addChapter(notStarted, read = false)

            val sourceManager = DesktopSourceManager().apply { registerBuiltinSource(MockSource(300L)) }
            val service = LibraryUpdateService(repo, OnlineMangaSyncService(repo, sourceManager))
            val explicitReport = service.updateLibrary(
                options = LibraryUpdateOptions(
                    skipCompleted = false,
                    skipNotStarted = true,
                    includedCategoryIds = setOf(includeCategory),
                    excludedCategoryIds = setOf(excludeCategory),
                ),
                throttleDelayMs = 0L,
            )
            assertEquals(listOf(included), explicitReport.results.map { it.mangaId })

            repo.upsertPreference(
                PreferenceSnapshotRecord(
                    key = "library_update_categories",
                    valueType = "STRING_SET",
                    valueJson = "[\"$includeCategory\"]",
                    importedAt = now,
                ),
            )
            repo.upsertPreference(
                PreferenceSnapshotRecord(
                    key = "library_update_categories_exclude",
                    valueType = "STRING_SET",
                    valueJson = "[\"$excludeCategory\"]",
                    importedAt = now,
                ),
            )
            val importedReport = service.updateLibrary(
                options = LibraryUpdateOptions(skipCompleted = false, skipNotStarted = true),
                throttleDelayMs = 0L,
            )
            assertEquals(listOf(included), importedReport.results.map { it.mangaId })
        } finally {
            repo.close()
        }
    }

    private class MockSource(
        override val id: Long,
        override val name: String = "Mock Source",
        override val lang: String = "en",
        override val supportsLatest: Boolean = true,
        private val chapters: List<SChapter> = emptyList(),
    ) : WindowsCatalogueSource {
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage =
            MangasPage(emptyList(), false)

        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = chapters
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
