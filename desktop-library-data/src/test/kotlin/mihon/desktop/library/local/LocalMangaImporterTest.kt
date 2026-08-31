package mihon.desktop.library.local

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

class LocalMangaImporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `import promotes immutable media and registers exact local identities idempotently`() {
        val source = createManga("source-漫画")
        val mediaRoot = tempDir.resolve("media-根")
        val database = tempDir.resolve("library.db")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val importer = LocalMangaImporter(repository)

            val first = importer.import(source, mediaRoot, nowMillis = 1_000)

            first.status shouldBe ImportStatus.SUCCEEDED
            first.counts.mangaInserted shouldBe 1L
            first.counts.chaptersInserted shouldBe 2L
            val manga = repository.librarySnapshot().single()
            manga.sourceId shouldBe LOCAL_SOURCE_ID
            manga.url shouldBe "local:${LocalImportScanner().scan(source).sha256}"
            repository.chapterSnapshot(manga.id).map { it.url }.toSet() shouldBe
                setOf("local:第 1 话", "local:第 2 话.CBZ")
            val localMangaRows = queryRows(database, "SELECT storage_path, manifest_sha256 FROM local_manga_entry")
            localMangaRows.size shouldBe 1
            val promoted = Path.of(localMangaRows.single().first()!!)
            promoted.startsWith(mediaRoot.toAbsolutePath().normalize().resolve("manga")) shouldBe true
            Files.readString(promoted.resolve("第 1 话").resolve("页 01.jpg")) shouldBe "page"
            queryRows(
                database,
                "SELECT relative_path, asset_kind FROM local_chapter_asset ORDER BY relative_path",
            ).toSet() shouldBe setOf(
                listOf("第 1 话", "DIRECTORY"),
                listOf("第 2 话.CBZ", "ARCHIVE"),
            )

            val second = importer.import(source, mediaRoot, nowMillis = 2_000)

            second.counts.mangaMerged shouldBe 1L
            second.counts.chaptersMerged shouldBe 2L
            repository.librarySnapshot().size shouldBe 1
            repository.chapterSnapshot(manga.id).size shouldBe 2
            queryRows(database, "SELECT storage_path FROM local_manga_entry") shouldBe
                listOf(listOf(promoted.toString()))
            listChildren(mediaRoot.resolve(".staging")).shouldBeEmpty()
            listChildren(mediaRoot.resolve("manga")) shouldBe listOf(promoted)
        }
    }

    @Test
    fun `source link swapped in after scan is rejected before promotion or database mutation`() {
        val source = createManga("race-source")
        val page = source.resolve("第 1 话").resolve("页 01.jpg")
        val outside = tempDir.resolve("outside.jpg")
        Files.writeString(outside, "outside")
        var linkCreated = false
        val checkpoint = object : LocalImportCheckpoint {
            override fun afterScan(manifest: LocalImportManifest) {
                Files.delete(page)
                linkCreated = runCatching {
                    Files.createSymbolicLink(page, outside)
                    true
                }.getOrDefault(false)
                if (!linkCreated && System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                    Files.delete(page.parent)
                    val outsideDirectory = Files.createDirectory(tempDir.resolve("outside-directory"))
                    Files.writeString(outsideDirectory.resolve("页 01.jpg"), "outside")
                    linkCreated = createImporterJunction(page.parent, outsideDirectory)
                }
            }
        }
        val database = tempDir.resolve("race.db")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val importer = LocalMangaImporter(repository, checkpoint = checkpoint)
            val error = runCatching { importer.import(source, tempDir.resolve("race-media"), 1) }.exceptionOrNull()
            linkCreated shouldBe true
            (error is LocalImportRejected) shouldBe true
            repository.librarySnapshot().shouldBeEmpty()
            repository.latestImportReport() shouldBe null
            assertNoImportResidue(tempDir.resolve("race-media"))
        }
    }

    @Test
    fun `copy promotion and report checkpoints roll files and every database row back`() {
        listOf("copy", "promotion", "report").forEach { failurePoint ->
            val caseRoot = Files.createDirectory(tempDir.resolve(failurePoint))
            val source = createMangaAt(caseRoot.resolve("source"))
            val mediaRoot = caseRoot.resolve("media")
            val database = caseRoot.resolve("library.db")
            DesktopLibraryDatabaseFactory.open(database).use { repository ->
                repository.insertManga(MangaRecord(sourceId = 99, url = "before", title = "before"))
                val before = dumpImportTables(database)
                val stagingCheckpoint = if (failurePoint == "copy") {
                    LocalStagingCheckpoint { error("after staging copy") }
                } else {
                    LocalStagingCheckpoint.NONE
                }
                val importCheckpoint = object : LocalImportCheckpoint {
                    override fun afterPromotion(staged: StagedLocalManga) {
                        if (failurePoint == "promotion") error("after promotion")
                    }

                    override fun beforeReport() {
                        if (failurePoint == "report") error("before report")
                    }
                }
                val importer = LocalMangaImporter(
                    mutations = repository,
                    stager = LocalImportStager(checkpoint = stagingCheckpoint),
                    checkpoint = importCheckpoint,
                )

                shouldThrow<IllegalStateException> { importer.import(source, mediaRoot, 2) }

                dumpImportTables(database) shouldBe before
                assertNoImportResidue(mediaRoot)
            }
        }
    }

    @Test
    fun `forged traversal absolute and duplicate manifests are rejected by staging`() {
        val source = createManga("forged")
        val original = LocalImportScanner().scan(source)
        val stager = LocalImportStager()
        listOf(
            original.copy(
                chapters = original.chapters.mapIndexed { index, value ->
                    if (index == 0) value.copy(relativePath = Path.of("..", "escape")) else value
                },
            ),
            original.copy(
                chapters = original.chapters.mapIndexed { index, value ->
                    if (index == 0) value.copy(relativePath = tempDir.resolve("absolute.cbz")) else value
                },
            ),
            original.copy(chapters = listOf(original.chapters.first(), original.chapters.first())),
        ).forEach { forged ->
            shouldThrow<LocalImportRejected> { stager.stage(forged, tempDir.resolve("forged-media")) }
            assertNoImportResidue(tempDir.resolve("forged-media"))
        }
    }

    @Test
    fun `existing promotion target is never overwritten and staging is removed`() {
        val source = createManga("collision")
        val mediaRoot = tempDir.resolve("collision-media")
        val existing = mediaRoot.resolve("manga").resolve("fixed-id")
        Files.createDirectories(existing)
        Files.writeString(existing.resolve("keep.txt"), "keep")
        val staged = LocalImportStager(idFactory = { "fixed-id" }).stage(LocalImportScanner().scan(source), mediaRoot)

        staged.use {
            shouldThrow<LocalImportRejected> { it.promote() }.message.shouldContain("promotion target already exists")
        }

        Files.readString(existing.resolve("keep.txt")) shouldBe "keep"
        listChildren(mediaRoot.resolve(".staging")).shouldBeEmpty()
    }

    @Test
    fun `existing staging target is never overwritten or deleted`() {
        val source = createManga("staging-collision")
        val mediaRoot = tempDir.resolve("staging-collision-media")
        val existing = mediaRoot.resolve(".staging").resolve("fixed-id")
        Files.createDirectories(existing)
        Files.writeString(existing.resolve("keep.txt"), "keep")

        shouldThrow<LocalImportRejected> {
            LocalImportStager(idFactory = { "fixed-id" }).stage(LocalImportScanner().scan(source), mediaRoot)
        }

        Files.readString(existing.resolve("keep.txt")) shouldBe "keep"
        listChildren(mediaRoot.resolve("manga")).shouldBeEmpty()
    }

    @Test
    fun `orphan cleanup deletes staging and unreferenced promotions but preserves database paths`() {
        val mediaRoot = tempDir.resolve("cleanup-media")
        val stagingOrphan = mediaRoot.resolve(".staging").resolve("stale")
        val finalOrphan = mediaRoot.resolve("manga").resolve("orphan")
        val retained = mediaRoot.resolve("manga").resolve("retained")
        listOf(stagingOrphan, finalOrphan, retained).forEach {
            Files.createDirectories(it)
            Files.writeString(it.resolve("asset"), it.fileName.toString())
        }
        val database = tempDir.resolve("cleanup.db")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val mangaId = repository.insertManga(MangaRecord(sourceId = 0, url = "local:kept", title = "kept"))
            val chapterId = repository.insertChapter(
                mihon.desktop.library.model.ChapterRecord(mangaId = mangaId, url = "local:chapter", name = "chapter"),
            )
            repository.insertLocalManga(LocalMangaRecord(mangaId, retained.toString(), "kept", 1))
            repository.insertLocalChapter(LocalChapterRecord(chapterId, "chapter", "DIRECTORY", 0, 0))

            LocalMangaImporter(repository).cleanupOrphans(mediaRoot)

            Files.exists(stagingOrphan) shouldBe false
            Files.exists(finalOrphan) shouldBe false
            Files.readString(retained.resolve("asset")) shouldBe "retained"
        }
    }

    private fun createManga(name: String): Path = createMangaAt(tempDir.resolve(name))

    private fun createMangaAt(source: Path): Path {
        val directoryChapter = source.resolve("第 1 话")
        Files.createDirectories(directoryChapter)
        Files.writeString(directoryChapter.resolve("页 01.jpg"), "page")
        Files.write(source.resolve("第 2 话.CBZ"), byteArrayOf(1, 2, 3))
        return source
    }

    private fun assertNoImportResidue(mediaRoot: Path) {
        listChildren(mediaRoot.resolve(".staging")).shouldBeEmpty()
        listChildren(mediaRoot.resolve("manga")).shouldBeEmpty()
    }
}

private val LOCAL_IMPORT_TABLES = listOf(
    "manga",
    "chapter",
    "local_manga_entry",
    "local_chapter_asset",
    "import_report",
    "import_report_item",
)

private fun dumpImportTables(path: Path): Map<String, List<List<String?>>> =
    LOCAL_IMPORT_TABLES.associateWith { table -> queryRows(path, "SELECT * FROM $table ORDER BY rowid") }

private fun queryRows(path: Path, sql: String): List<List<String?>> =
    DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                val columns = result.metaData.columnCount
                buildList {
                    while (result.next()) add((1..columns).map { result.getObject(it)?.toString() })
                }
            }
        }
    }

private fun listChildren(directory: Path): List<Path> =
    if (Files.notExists(directory)) {
        emptyList()
    } else {
        Files.list(directory).use { it.sorted().toList() }
    }

private fun createImporterJunction(link: Path, target: Path): Boolean =
    ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
        .redirectErrorStream(true)
        .start()
        .waitFor() == 0
