package mihon.desktop.library.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SqlDelightLibraryRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `committed library survives reopen and rollback never leaks rows`() {
        val file = tempDir.resolve("library.db")
        DesktopLibraryDatabaseFactory.open(file).use { first ->
            first.transaction {
                val id = insertManga(MangaRecord(sourceId = 1, url = "/a", title = "漫画 A"))
                insertChapter(ChapterRecord(mangaId = id, url = "/2", name = "第 2 话", sourceOrder = 2))
                insertChapter(
                    ChapterRecord(
                        mangaId = id,
                        url = "/1",
                        name = "第 1 话",
                        sourceOrder = 1,
                        read = true,
                    ),
                )
            }
            shouldThrow<IllegalStateException> {
                first.transaction {
                    insertManga(MangaRecord(sourceId = 1, url = "/rolled-back", title = "不可见"))
                    error("forced rollback")
                }
            }
        }

        DesktopLibraryDatabaseFactory.open(file).use { reopened ->
            reopened.librarySnapshot().single().run {
                title shouldBe "漫画 A"
                chapterCount shouldBe 2L
                unreadCount shouldBe 1L
            }
            val mangaId = reopened.librarySnapshot().single().id
            reopened.chapterSnapshot(mangaId).map { it.name } shouldBe listOf("第 2 话", "第 1 话")
            reopened.findManga(1, "/rolled-back") shouldBe null
        }
    }

    @Test
    fun `foreign keys are enabled again when an existing database is reopened`() {
        val file = tempDir.resolve("foreign-keys.db")
        DesktopLibraryDatabaseFactory.open(file).close()

        DesktopLibraryDatabaseFactory.open(file).use { reopened ->
            shouldThrowAny {
                reopened.insertChapter(ChapterRecord(mangaId = 999, url = "/missing", name = "missing"))
            }
            reopened.librarySnapshot().shouldBeEmpty()
        }
    }

    @Test
    fun `all manga and chapter fields map losslessly and observations emit changes`(): Unit = runBlocking {
        val file = tempDir.resolve("fields.db")
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            val mangaId = repository.insertManga(
                MangaRecord(
                    sourceId = 42,
                    url = "/series",
                    title = "Title",
                    artist = "Artist",
                    author = "Author",
                    description = "Description",
                    genreJson = "[\"Action\"]",
                    status = 2,
                    thumbnailUrl = "https://example.invalid/cover.jpg",
                    favorite = true,
                    dateAdded = 100,
                    viewerFlags = 3,
                    chapterFlags = 4,
                    updateStrategy = "ONLY_FETCH_ONCE",
                    lastModifiedAt = 200,
                    favoriteModifiedAt = 201,
                    excludedScanlatorsJson = "[\"Group\"]",
                    version = 5,
                    notes = "Notes",
                    initialized = true,
                    memoJson = "{\"key\":\"value\"}",
                ),
            )
            val categoryId = repository.upsertCategory(CategoryRecord(name = "Imported", sortOrder = 7, flags = 8))
            repository.linkCategory(mangaId, categoryId)
            val chapterId = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/chapter",
                    name = "Chapter",
                    scanlator = "Group",
                    read = true,
                    bookmark = true,
                    lastPageRead = 9,
                    dateFetch = 10,
                    dateUpload = 11,
                    chapterNumber = 12.5,
                    sourceOrder = 13,
                    lastModifiedAt = 14,
                    version = 15,
                    memoJson = "{\"chapter\":true}",
                ),
            )

            repository.observeLibrary().first() shouldBe repository.librarySnapshot()
            repository.observeManga(mangaId).first() shouldBe repository.mangaSnapshot(mangaId)
            repository.observeChapters(mangaId).first() shouldBe repository.chapterSnapshot(mangaId)

            repository.mangaSnapshot(mangaId) shouldBe MangaDetails(
                id = mangaId,
                sourceId = 42,
                url = "/series",
                title = "Title",
                artist = "Artist",
                author = "Author",
                description = "Description",
                genreJson = "[\"Action\"]",
                status = 2,
                thumbnailUrl = "https://example.invalid/cover.jpg",
                favorite = true,
                dateAdded = 100,
                viewerFlags = 3,
                chapterFlags = 4,
                updateStrategy = "ONLY_FETCH_ONCE",
                lastModifiedAt = 200,
                favoriteModifiedAt = 201,
                excludedScanlatorsJson = "[\"Group\"]",
                version = 5,
                notes = "Notes",
                initialized = true,
                memoJson = "{\"key\":\"value\"}",
                categories = listOf(CategoryRecord(categoryId, "Imported", 7, 8)),
            )
            repository.chapterSnapshot(mangaId).single() shouldBe LibraryChapter(
                id = chapterId,
                mangaId = mangaId,
                url = "/chapter",
                name = "Chapter",
                scanlator = "Group",
                read = true,
                bookmark = true,
                lastPageRead = 9,
                dateFetch = 10,
                dateUpload = 11,
                chapterNumber = 12.5,
                sourceOrder = 13,
                lastModifiedAt = 14,
                version = 15,
                memoJson = "{\"chapter\":true}",
            )
        }
    }

    @Test
    fun `mutation ports update records and tracking identity ignores remote id changes`() {
        val file = tempDir.resolve("mutations.db")
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            val mangaId = repository.insertManga(MangaRecord(sourceId = 7, url = "/m", title = "Old"))
            repository.updateManga(repository.findManga(7, "/m")!!.copy(title = "New"))
            val chapterId = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/c", name = "Old"))
            repository.updateChapter(repository.findChapter(mangaId, "/c")!!.copy(name = "New"))
            repository.upsertHistory(HistoryRecord(chapterId, lastRead = 100, readDuration = 200))

            repository.insertTracking(TrackingRecord(mangaId = mangaId, trackerId = 9, remoteId = 10, title = "Remote"))
            val tracking = repository.findTracking(mangaId, trackerId = 9)!!
            tracking.remoteId shouldBe 10
            repository.updateTracking(tracking.copy(remoteId = 11, lastChapterRead = 12.0))
            repository.findTracking(mangaId, trackerId = 9)!!.run {
                remoteId shouldBe 11
                lastChapterRead shouldBe 12.0
            }

            repository.findManga(7, "/m")!!.title shouldBe "New"
            repository.findChapter(mangaId, "/c")!!.name shouldBe "New"
        }
    }

    @Test
    fun `metadata local records and latest report persist through the mutation port`() {
        val file = tempDir.resolve("metadata.db")
        var reportId = 0L
        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            val mangaId = repository.insertManga(MangaRecord(sourceId = 3, url = "/local", title = "Local"))
            val chapterId = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/one", name = "One"))
            repository.upsertSource(SourceRecord(3, "Source", 100))
            repository.upsertPreference(PreferenceSnapshotRecord("theme", "STRING", "\"dark\"", 101))
            repository.upsertSourcePreference(
                SourcePreferenceSnapshotRecord("3", "quality", "STRING", "\"high\"", 102),
            )
            repository.insertLocalManga(LocalMangaRecord(mangaId, "manga/path", "sha256", 103))
            repository.insertLocalChapter(LocalChapterRecord(chapterId, "chapter/path", "DIRECTORY", 104, 105))
            reportId = repository.insertReport(
                ImportReportRecord(
                    importType = ImportType.LOCAL_DIRECTORY,
                    sourcePath = "source/path",
                    status = ImportStatus.SUCCEEDED,
                    startedAt = 106,
                    finishedAt = 107,
                    counts = ImportCounts(mangaInserted = 1, chaptersInserted = 1, preferencesImported = 2),
                ),
            )
            repository.insertReportItem(
                reportId,
                ImportReportItemRecord("PREFERENCE", "private", "SKIPPED", "PRIVATE", "Skipped private value"),
            )
            repository.insertReportItem(
                reportId,
                ImportReportItemRecord("SOURCE", "42", "IMPORTED", null, "Imported source"),
            )
        }

        DesktopLibraryDatabaseFactory.open(file).use { repository ->
            repository.latestImportReport()!!.run {
                id shouldBe reportId
                importType shouldBe ImportType.LOCAL_DIRECTORY
                sourcePath shouldBe "source/path"
                status shouldBe ImportStatus.SUCCEEDED
                counts shouldBe ImportCounts(mangaInserted = 1, chaptersInserted = 1, preferencesImported = 2)
                items.map { listOf(it.itemType, it.itemKey, it.outcome, it.reason, it.message) } shouldBe
                    listOf(
                        listOf("PREFERENCE", "private", "SKIPPED", "PRIVATE", "Skipped private value"),
                        listOf("SOURCE", "42", "IMPORTED", null, "Imported source"),
                    )
            }
        }
    }

    @Test
    fun `closing the repository closes its owned writer`() {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("closed.db"))
        repository.close()

        shouldThrowAny { repository.librarySnapshot() }
    }

    @Test
    fun `concurrent mutation waits for an uncommitted transaction and commits independently`() {
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("concurrent.db"))
        val transactionStarted = CountDownLatch(1)
        val concurrentStarted = CountDownLatch(1)
        val concurrentFinished = CountDownLatch(1)
        val allowTransactionEnd = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val transaction = executor.submit {
                shouldThrow<IllegalStateException> {
                    repository.transaction {
                        insertManga(MangaRecord(sourceId = 1, url = "/rolled-back", title = "Rolled back"))
                        transactionStarted.countDown()
                        check(allowTransactionEnd.await(5, TimeUnit.SECONDS))
                        error("forced rollback")
                    }
                }
            }
            check(transactionStarted.await(5, TimeUnit.SECONDS))

            val concurrentMutation = executor.submit {
                concurrentStarted.countDown()
                repository.insertManga(MangaRecord(sourceId = 2, url = "/committed", title = "Committed"))
                concurrentFinished.countDown()
            }
            check(concurrentStarted.await(5, TimeUnit.SECONDS))

            concurrentFinished.await(1, TimeUnit.SECONDS) shouldBe false
            allowTransactionEnd.countDown()
            transaction.get(5, TimeUnit.SECONDS)
            concurrentMutation.get(5, TimeUnit.SECONDS)

            repository.findManga(1, "/rolled-back") shouldBe null
            repository.findManga(2, "/committed")!!.title shouldBe "Committed"
        } finally {
            allowTransactionEnd.countDown()
            executor.shutdownNow()
            repository.close()
        }
    }

    @Test
    fun `concurrent manga inserts return the IDs of their own rows`() {
        val firstInsertPaused = CountDownLatch(1)
        val allowFirstInsertToReturn = CountDownLatch(1)
        val secondInsertAttempted = CountDownLatch(1)
        val secondInsertFinished = CountDownLatch(1)
        val listenerInvocations = AtomicInteger()
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("insert-ids.db")) { sql ->
            if (sql.startsWith("INSERT INTO manga(") && listenerInvocations.incrementAndGet() == 1) {
                firstInsertPaused.countDown()
                check(allowFirstInsertToReturn.await(5, TimeUnit.SECONDS))
            }
        }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<Long> {
                repository.insertManga(MangaRecord(sourceId = 101, url = "/first", title = "First"))
            }
            check(firstInsertPaused.await(5, TimeUnit.SECONDS))

            val second = executor.submit<Long> {
                secondInsertAttempted.countDown()
                try {
                    repository.insertManga(MangaRecord(sourceId = 102, url = "/second", title = "Second"))
                } finally {
                    secondInsertFinished.countDown()
                }
            }
            check(secondInsertAttempted.await(5, TimeUnit.SECONDS))
            secondInsertFinished.await(1, TimeUnit.SECONDS) shouldBe false

            allowFirstInsertToReturn.countDown()
            val firstId = first.get(5, TimeUnit.SECONDS)
            val secondId = second.get(5, TimeUnit.SECONDS)

            firstId shouldBe repository.findManga(101, "/first")!!.id
            secondId shouldBe repository.findManga(102, "/second")!!.id
            (firstId == secondId) shouldBe false
        } finally {
            allowFirstInsertToReturn.countDown()
            executor.shutdownNow()
            repository.close()
        }
    }

    @Test
    fun `category CRUD, sorting, and manga category assignment work as expected`(): Unit = runBlocking {
        val file = tempDir.resolve("categories.db")
        DesktopLibraryDatabaseFactory.open(file).use { repo ->
            val cat1 = repo.upsertCategory(CategoryRecord(name = "Action", sortOrder = 2))
            val cat2 = repo.upsertCategory(CategoryRecord(name = "Comedy", sortOrder = 1))

            val categories = repo.categoriesSnapshot()
            categories.map { it.name } shouldBe listOf("Comedy", "Action")

            repo.updateCategoryName(cat1, "Action/Adventure")
            repo.updateCategoryOrder(cat1, 0)
            repo.categoriesSnapshot().first().name shouldBe "Action/Adventure"

            val mangaId = repo.insertManga(MangaRecord(sourceId = 1, url = "/manga", title = "Manga"))
            repo.setMangaCategories(mangaId, listOf(cat1, cat2))
            val details = repo.mangaSnapshot(mangaId)
            details!!.categories.map { it.id }.toSet() shouldBe setOf(cat1, cat2)

            repo.deleteCategory(cat2)
            repo.categoriesSnapshot().map { it.id } shouldBe listOf(cat1)
            repo.mangaSnapshot(mangaId)!!.categories.map { it.id } shouldBe listOf(cat1)
        }
    }

    @Test
    fun `history records and details querying, deletion, and clearAll work`(): Unit = runBlocking {
        val file = tempDir.resolve("history.db")
        DesktopLibraryDatabaseFactory.open(file).use { repo ->
            val mangaId = repo.insertManga(MangaRecord(sourceId = 1, url = "/manga", title = "Berserk"))
            val chapId = repo.insertChapter(ChapterRecord(mangaId = mangaId, url = "/c1", name = "Chapter 1"))

            repo.upsertHistory(HistoryRecord(chapterId = chapId, lastRead = 1000L, readDuration = 60L))

            val history = repo.historySnapshot()
            history.size shouldBe 1
            history.first().mangaTitle shouldBe "Berserk"
            history.first().chapterName shouldBe "Chapter 1"
            history.first().lastRead shouldBe 1000L

            val searchFiltered = repo.historySnapshot("Naruto")
            searchFiltered.shouldBeEmpty()

            val searchMatched = repo.historySnapshot("serk")
            searchMatched.size shouldBe 1

            repo.deleteHistory(chapId)
            repo.historySnapshot().shouldBeEmpty()

            repo.upsertHistory(HistoryRecord(chapterId = chapId, lastRead = 2000L, readDuration = 120L))
            repo.historySnapshot().size shouldBe 1
            repo.clearAllHistory()
            repo.historySnapshot().shouldBeEmpty()
        }
    }

    @Test
    fun `tracking insertion, update, query and deletion work`(): Unit = runBlocking {
        val file = tempDir.resolve("tracking.db")
        DesktopLibraryDatabaseFactory.open(file).use { repo ->
            val mangaId = repo.insertManga(MangaRecord(sourceId = 1, url = "/manga", title = "One Piece"))
            repo.insertTracking(
                TrackingRecord(
                    mangaId = mangaId,
                    trackerId = 1, // MAL
                    remoteId = 13,
                    title = "One Piece",
                    lastChapterRead = 1000.0,
                    score = 9.5,
                ),
            )

            val tracks = repo.trackingSnapshot(mangaId)
            tracks.size shouldBe 1
            tracks.first().trackerId shouldBe 1L
            tracks.first().lastChapterRead shouldBe 1000.0

            repo.updateTracking(tracks.first().copy(lastChapterRead = 1001.0))
            repo.trackingSnapshot(mangaId).first().lastChapterRead shouldBe 1001.0

            repo.deleteTracking(mangaId, 1L)
            repo.trackingSnapshot(mangaId).shouldBeEmpty()
        }
    }

    @Test
    fun `export snapshots return full database state correctly`(): Unit = runBlocking {
        val file = tempDir.resolve("export-snapshot.db")
        DesktopLibraryDatabaseFactory.open(file).use { repo ->
            val mangaId = repo.insertManga(MangaRecord(sourceId = 42, url = "/manga1", title = "Export Manga"))
            val catId = repo.upsertCategory(CategoryRecord(name = "Export Cat", sortOrder = 1))
            repo.linkCategory(mangaId, catId)
            val chapterId = repo.insertChapter(ChapterRecord(mangaId = mangaId, url = "/c1", name = "Ch 1"))
            repo.upsertHistory(HistoryRecord(chapterId, lastRead = 5000L, readDuration = 120L))
            repo.insertTracking(TrackingRecord(mangaId = mangaId, trackerId = 2, remoteId = 20, title = "Tracked"))
            repo.upsertSource(SourceRecord(42L, "Test Source", 1000L))
            repo.upsertPreference(PreferenceSnapshotRecord("pref_key", "STRING", "\"val\"", 1000L))
            repo.upsertSourcePreference(SourcePreferenceSnapshotRecord("src", "k", "INT", "1", 1000L))

            repo.allMangaSnapshot().size shouldBe 1
            repo.allMangaSnapshot().first().title shouldBe "Export Manga"

            repo.allChaptersSnapshot().size shouldBe 1
            repo.allChaptersSnapshot().first().name shouldBe "Ch 1"

            repo.allCategoriesSnapshot().size shouldBe 1
            repo.allCategoriesSnapshot().first().name shouldBe "Export Cat"

            repo.mangaCategoryLinksSnapshot()[mangaId] shouldBe listOf(catId)

            repo.allHistorySnapshot().size shouldBe 1
            repo.allHistorySnapshot().first().readDuration shouldBe 120L

            repo.allTrackingSnapshot().size shouldBe 1
            repo.allTrackingSnapshot().first().remoteId shouldBe 20L

            repo.allSourcesSnapshot().size shouldBe 1
            repo.allSourcesSnapshot().first().name shouldBe "Test Source"

            repo.allPreferenceSnapshots().size shouldBe 1
            repo.allPreferenceSnapshots().first().key shouldBe "pref_key"

            repo.allSourcePreferenceSnapshots().size shouldBe 1
            repo.allSourcePreferenceSnapshots().first().sourceKey shouldBe "src"
        }
    }
}
