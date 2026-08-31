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
}
