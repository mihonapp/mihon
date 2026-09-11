package mihon.desktop.library.reader

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.reader.session.ProgressWriteResult
import mihon.reader.session.ReaderProgressUpdate
import mihon.reader.source.ChapterDirection
import mihon.reader.source.ReaderChapterAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SqlDelightReaderLibraryPortTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `asset lookup maps every field preserves Long progress and normalizes a contained path`() {
        open("asset.db").use { repository ->
            val storageRoot = tempDir.resolve("library").resolve("nested").resolve("..").toAbsolutePath()
            val mangaId = repository.insertManga(MangaRecord(sourceId = 7, url = "/m", title = "Manga"))
            val chapterId = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/c",
                    name = "Chapter",
                    read = true,
                    lastPageRead = Int.MAX_VALUE.toLong() + 41,
                ),
            )
            repository.insertLocalManga(LocalMangaRecord(mangaId, storageRoot.toString(), "hash", 1))
            repository.insertLocalChapter(
                LocalChapterRecord(chapterId, "pages/../chapter.cbz", "ARCHIVE", 123, 456),
            )

            val port: ReaderLibraryPort = repository
            port.chapterAsset(chapterId) shouldBe ReaderChapterAsset(
                mangaId = mangaId,
                chapterId = chapterId,
                mangaTitle = "Manga",
                chapterName = "Chapter",
                storageRoot = storageRoot.normalize(),
                relativePath = Path.of("chapter.cbz"),
                assetKind = "ARCHIVE",
                sizeBytes = 123,
                modifiedAt = 456,
                lastPageRead = Int.MAX_VALUE.toLong() + 41,
                read = true,
            )
        }
    }

    @Test
    fun `asset lookup rejects missing absolute and escaping relative assets`() {
        open("unsafe-assets.db").use { repository ->
            val root = tempDir.resolve("root").toAbsolutePath().normalize()
            val mangaId = repository.insertManga(MangaRecord(sourceId = 1, url = "/m", title = "Manga"))
            repository.insertLocalManga(LocalMangaRecord(mangaId, root.toString(), "hash", 1))
            val missing = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/missing", name = "Missing"))
            val escaping = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/escape", name = "Escape"))
            val absolute = repository.insertChapter(
                ChapterRecord(mangaId = mangaId, url = "/absolute", name = "Absolute"),
            )
            repository.insertLocalChapter(LocalChapterRecord(escaping, "../outside.cbz", "ARCHIVE", 1, 1))
            repository.insertLocalChapter(
                LocalChapterRecord(
                    absolute,
                    tempDir.resolve("absolute.cbz").toAbsolutePath().toString(),
                    "ARCHIVE",
                    1,
                    1,
                ),
            )

            repository.chapterAsset(missing) shouldBe null
            repository.chapterAsset(escaping) shouldBe null
            repository.chapterAsset(absolute) shouldBe null
        }
    }

    @Test
    fun `adjacency follows full detail order with name and id ties and skips unreadable chapters`() {
        open("adjacent.db").use { repository ->
            val root = tempDir.resolve("adjacent-root").toAbsolutePath()
            val mangaId = repository.insertManga(MangaRecord(sourceId = 2, url = "/m", title = "Manga"))
            repository.insertLocalManga(LocalMangaRecord(mangaId, root.toString(), "hash", 1))

            val first = readableChapter(repository, mangaId, "/first", "alpha", 5, 10.0)
            val second = readableChapter(repository, mangaId, "/second", "Alpha", 5, 10.0)
            val higherNumber = readableChapter(repository, mangaId, "/higher", "zulu", 5, 11.0)
            val unreadable = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/unreadable",
                    name = "bravo",
                    sourceOrder = 5,
                    chapterNumber = 10.0,
                ),
            )
            val third = readableChapter(repository, mangaId, "/third", "charlie", 5, 10.0)
            val fourth = readableChapter(repository, mangaId, "/fourth", "delta", 4, 99.0)

            // NOCASE makes alpha/Alpha equal, so the later (higher) ID sorts first.
            repository.adjacentReadableChapter(higherNumber, ChapterDirection.PREVIOUS) shouldBe null
            repository.adjacentReadableChapter(higherNumber, ChapterDirection.NEXT)?.chapterId shouldBe second
            repository.adjacentReadableChapter(second, ChapterDirection.PREVIOUS)?.chapterId shouldBe higherNumber
            repository.adjacentReadableChapter(second, ChapterDirection.NEXT)?.chapterId shouldBe first
            repository.adjacentReadableChapter(first, ChapterDirection.PREVIOUS)?.chapterId shouldBe second
            repository.adjacentReadableChapter(first, ChapterDirection.NEXT)?.chapterId shouldBe third
            repository.adjacentReadableChapter(third, ChapterDirection.NEXT)?.chapterId shouldBe fourth
            repository.adjacentReadableChapter(fourth, ChapterDirection.NEXT) shouldBe null
            repository.adjacentReadableChapter(unreadable, ChapterDirection.PREVIOUS) shouldBe null
            repository.adjacentReadableChapter(unreadable, ChapterDirection.NEXT) shouldBe null
        }
    }

    @Test
    fun `adjacency skips a path-invalid nearest candidate`() {
        open("unsafe-candidate.db").use { repository ->
            val root = tempDir.resolve("unsafe-candidate-root").toAbsolutePath()
            val mangaId = repository.insertManga(MangaRecord(sourceId = 3, url = "/m", title = "Manga"))
            repository.insertLocalManga(LocalMangaRecord(mangaId, root.toString(), "hash", 1))
            val current = readableChapter(repository, mangaId, "/current", "Current", 3, 3.0)
            val unsafePrevious = readableChapter(repository, mangaId, "/unsafe-previous", "Unsafe Previous", 4, 4.0)
            val safePreceding = readableChapter(repository, mangaId, "/safe-previous", "Safe Previous", 5, 5.0)
            val unsafeNearest = readableChapter(repository, mangaId, "/unsafe", "Unsafe", 2, 2.0)
            val safeFollowing = readableChapter(repository, mangaId, "/safe", "Safe", 1, 1.0)
            repository.insertLocalChapter(
                LocalChapterRecord(unsafePrevious, "../outside-previous.cbz", "ARCHIVE", 1, 1),
            )
            repository.insertLocalChapter(
                LocalChapterRecord(unsafeNearest, "../outside.cbz", "ARCHIVE", 1, 1),
            )

            repository.adjacentReadableChapter(current, ChapterDirection.PREVIOUS)?.chapterId shouldBe safePreceding
            repository.adjacentReadableChapter(current, ChapterDirection.NEXT)?.chapterId shouldBe safeFollowing
        }
    }

    @Test
    fun `adjacency rejects a path-invalid current asset`() {
        open("unsafe-current.db").use { repository ->
            val root = tempDir.resolve("unsafe-current-root").toAbsolutePath()
            val mangaId = repository.insertManga(MangaRecord(sourceId = 4, url = "/m", title = "Manga"))
            repository.insertLocalManga(LocalMangaRecord(mangaId, root.toString(), "hash", 1))
            val unsafeCurrent = readableChapter(repository, mangaId, "/current", "Current", 2, 2.0)
            readableChapter(repository, mangaId, "/previous", "Previous", 3, 3.0)
            readableChapter(repository, mangaId, "/next", "Next", 1, 1.0)
            repository.insertLocalChapter(
                LocalChapterRecord(unsafeCurrent, "../outside.cbz", "ARCHIVE", 1, 1),
            )

            repository.adjacentReadableChapter(unsafeCurrent, ChapterDirection.PREVIOUS) shouldBe null
            repository.adjacentReadableChapter(unsafeCurrent, ChapterDirection.NEXT) shouldBe null
        }
    }

    @Test
    fun `online metadata and adjacency resolve chapters without local assets`() {
        open("online.db").use { repository ->
            val mangaId = repository.insertManga(MangaRecord(sourceId = 77, url = "/online", title = "Online Manga"))
            val first = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/first",
                    name = "alpha",
                    sourceOrder = 5,
                    chapterNumber = 10.0,
                    dateUpload = 111,
                    scanlator = "Group",
                ),
            )
            val second = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/second",
                    name = "Alpha",
                    sourceOrder = 5,
                    chapterNumber = 10.0,
                ),
            )
            val higherNumber = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/higher",
                    name = "zulu",
                    sourceOrder = 5,
                    chapterNumber = 11.0,
                ),
            )
            val third = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/third",
                    name = "charlie",
                    sourceOrder = 5,
                    chapterNumber = 10.0,
                ),
            )
            val fourth = repository.insertChapter(
                ChapterRecord(
                    mangaId = mangaId,
                    url = "/fourth",
                    name = "delta",
                    sourceOrder = 4,
                    chapterNumber = 99.0,
                ),
            )

            repository.onlineChapter(second) shouldBe ReaderOnlineChapter(
                mangaId = mangaId,
                chapterId = second,
                mangaTitle = "Online Manga",
                chapterName = "Alpha",
                chapterUrl = "/second",
                sourceId = 77,
                chapterNumber = 10.0,
                sourceOrder = 5,
                dateUpload = 0,
                scanlator = null,
                lastPageRead = 0,
                read = false,
            )
            repository.onlineChapter(first)?.scanlator shouldBe "Group"
            repository.onlineChapter(first)?.dateUpload shouldBe 111
            repository.onlineChapter(Long.MAX_VALUE) shouldBe null

            repository.adjacentOnlineChapter(higherNumber, ChapterDirection.PREVIOUS) shouldBe null
            repository.adjacentOnlineChapter(higherNumber, ChapterDirection.NEXT)?.chapterId shouldBe second
            repository.adjacentOnlineChapter(second, ChapterDirection.PREVIOUS)?.chapterId shouldBe higherNumber
            repository.adjacentOnlineChapter(second, ChapterDirection.NEXT)?.chapterId shouldBe first
            repository.adjacentOnlineChapter(first, ChapterDirection.PREVIOUS)?.chapterId shouldBe second
            repository.adjacentOnlineChapter(first, ChapterDirection.NEXT)?.chapterId shouldBe third
            repository.adjacentOnlineChapter(third, ChapterDirection.NEXT)?.chapterId shouldBe fourth
            repository.adjacentOnlineChapter(fourth, ChapterDirection.NEXT) shouldBe null
        }
    }

    @Test
    fun `progress stores backward position keeps completion monotonic and accumulates duration`(): Unit = runBlocking {
        open("progress.db").use { repository ->
            val chapterId = chapter(repository)

            repository.record(
                update(
                    chapterId,
                    page = 25,
                    completed = true,
                    readAt = 200,
                    duration = 30,
                    generation = 1,
                    sequence = 1,
                ),
            ) shouldBe
                ProgressWriteResult.APPLIED
            repository.record(
                update(
                    chapterId,
                    page = 4,
                    completed = false,
                    readAt = 100,
                    duration = 12,
                    generation = 1,
                    sequence = 2,
                ),
            ) shouldBe
                ProgressWriteResult.APPLIED

            repository.chapterSnapshot(repository.librarySnapshot().single().id).single().run {
                lastPageRead shouldBe 4
                read shouldBe true
            }
            history("progress.db", chapterId) shouldBe (200L to 42L)
        }
    }

    @Test
    fun `stale generations sequences and equal versions never mutate progress`(): Unit = runBlocking {
        open("stale.db").use { repository ->
            val chapterId = chapter(repository)
            repository.record(update(chapterId, page = 10, generation = 2, sequence = 5)) shouldBe
                ProgressWriteResult.APPLIED

            repository.record(update(chapterId, page = 11, generation = 1, sequence = 99)) shouldBe
                ProgressWriteResult.STALE
            repository.record(update(chapterId, page = 12, generation = 2, sequence = 4)) shouldBe
                ProgressWriteResult.STALE
            repository.record(update(chapterId, page = 13, generation = 2, sequence = 5)) shouldBe
                ProgressWriteResult.STALE

            repository.chapterSnapshot(repository.librarySnapshot().single().id).single().lastPageRead shouldBe 10
        }
    }

    @Test
    fun `repository rejects values beyond persistence bounds without wrapping`() {
        open("bounds.db").use { repository ->
            val chapterId = chapter(repository)
            shouldThrow<IllegalArgumentException> {
                runBlocking { repository.record(update(chapterId, page = Int.MAX_VALUE.toLong() + 1)) }
            }
            shouldThrow<IllegalArgumentException> {
                runBlocking { repository.record(update(chapterId, duration = 86_400_001)) }
            }
            repository.chapterSnapshot(repository.librarySnapshot().single().id).single().lastPageRead shouldBe 0
        }
    }

    @Test
    fun `duration accumulation saturates before SQLite integer overflow`(): Unit = runBlocking {
        open("saturation.db").use { repository ->
            val chapterId = chapter(repository)
            repository.upsertHistory(HistoryRecord(chapterId, lastRead = 5, readDuration = Long.MAX_VALUE - 3))

            repository.record(update(chapterId, duration = 10, readAt = 6)) shouldBe ProgressWriteResult.APPLIED

            history("saturation.db", chapterId) shouldBe (6L to Long.MAX_VALUE)
        }
    }

    @Test
    fun `later history failure rolls back chapter mutation and accepted version so the same pair can retry`(): Unit =
        runBlocking {
            open("rollback.db").use { repository ->
                val chapterId = chapter(repository)
                repository.upsertHistory(HistoryRecord(chapterId, lastRead = 5, readDuration = 6))
                execute(
                    "rollback.db",
                    "CREATE TRIGGER fail_reader_progress BEFORE UPDATE OF last_read, read_duration ON history " +
                        "BEGIN SELECT RAISE(FAIL, 'forced rollback'); END",
                )
                val attempted =
                    update(
                        chapterId,
                        page = 8,
                        completed = true,
                        readAt = 9,
                        duration = 10,
                        generation = 3,
                        sequence = 4,
                    )

                shouldThrowAny { repository.record(attempted) }
                repository.chapterSnapshot(repository.librarySnapshot().single().id).single().run {
                    lastPageRead shouldBe 0
                    read shouldBe false
                }
                history("rollback.db", chapterId) shouldBe (5L to 6L)

                execute("rollback.db", "DROP TRIGGER fail_reader_progress")
                repository.record(attempted) shouldBe ProgressWriteResult.APPLIED
                repository.chapterSnapshot(repository.librarySnapshot().single().id).single().run {
                    lastPageRead shouldBe 8
                    read shouldBe true
                }
                history("rollback.db", chapterId) shouldBe (9L to 16L)
            }
        }

    @Test
    fun `mutex serializes concurrent writes and rejects a delayed older pair`(): Unit = runBlocking {
        val firstChapterUpdate = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        var pause = true
        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("concurrent.db")) { sql ->
            if (pause && sql.startsWith("UPDATE chapter") && sql.contains("last_page_read")) {
                pause = false
                firstChapterUpdate.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS))
            }
        }
        try {
            val chapterId = chapter(repository)
            val newer =
                async(Dispatchers.IO) { repository.record(update(chapterId, page = 20, generation = 5, sequence = 2)) }
            check(firstChapterUpdate.await(5, TimeUnit.SECONDS))
            val older =
                async(Dispatchers.IO) { repository.record(update(chapterId, page = 10, generation = 5, sequence = 1)) }
            releaseFirst.countDown()

            newer.await() shouldBe ProgressWriteResult.APPLIED
            older.await() shouldBe ProgressWriteResult.STALE
            repository.chapterSnapshot(repository.librarySnapshot().single().id).single().lastPageRead shouldBe 20
        } finally {
            releaseFirst.countDown()
            repository.close()
        }
    }

    @Test
    fun `reader progress invalidates the real chapter observation flow`(): Unit = runBlocking {
        open("flow.db").use { repository ->
            val mangaId = repository.insertManga(MangaRecord(sourceId = 1, url = "/m", title = "Manga"))
            val chapterId = repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/c", name = "Chapter"))
            val firstEmission = CompletableDeferred<Unit>()
            val emissions = mutableListOf<Long>()
            val collector = launch {
                repository.observeChapters(mangaId)
                    .onEach { if (emissions.isEmpty()) firstEmission.complete(Unit) }
                    .take(2)
                    .toList()
                    .forEach { emissions += it.single().lastPageRead }
            }
            withTimeout(5_000) { firstEmission.await() }

            repository.record(update(chapterId, page = 7)) shouldBe ProgressWriteResult.APPLIED
            withTimeout(5_000) { collector.join() }

            emissions shouldBe listOf(0L, 7L)
        }
    }

    private fun open(fileName: String): SqlDelightLibraryRepository =
        DesktopLibraryDatabaseFactory.open(tempDir.resolve(fileName))

    private fun chapter(repository: SqlDelightLibraryRepository): Long {
        val mangaId = repository.insertManga(MangaRecord(sourceId = 1, url = "/m", title = "Manga"))
        return repository.insertChapter(ChapterRecord(mangaId = mangaId, url = "/c", name = "Chapter"))
    }

    private fun readableChapter(
        repository: SqlDelightLibraryRepository,
        mangaId: Long,
        url: String,
        name: String,
        sourceOrder: Long,
        chapterNumber: Double,
    ): Long {
        val id = repository.insertChapter(
            ChapterRecord(
                mangaId = mangaId,
                url = url,
                name = name,
                sourceOrder = sourceOrder,
                chapterNumber = chapterNumber,
            ),
        )
        repository.insertLocalChapter(LocalChapterRecord(id, "$id.cbz", "ARCHIVE", id, id))
        return id
    }

    private fun update(
        chapterId: Long,
        page: Long = 0,
        completed: Boolean = false,
        readAt: Long = 0,
        duration: Long = 0,
        generation: Long = 1,
        sequence: Long = 1,
    ) = ReaderProgressUpdate(
        chapterId = chapterId,
        pageIndex = page,
        completed = completed,
        lastReadEpochMillis = readAt,
        readDurationDeltaMillis = duration,
        generation = generation,
        sequence = sequence,
    )

    private fun history(fileName: String, chapterId: Long): Pair<Long, Long> =
        checkNotNull(historyOrNull(fileName, chapterId))

    private fun historyOrNull(fileName: String, chapterId: Long): Pair<Long, Long>? =
        DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve(fileName).toAbsolutePath()}").use { connection ->
            connection.prepareStatement(
                "SELECT last_read, read_duration FROM history WHERE chapter_id = ?",
            ).use { statement ->
                statement.setLong(1, chapterId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getLong(1) to rows.getLong(2) else null
                }
            }
        }

    private fun execute(fileName: String, sql: String) {
        DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve(fileName).toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
