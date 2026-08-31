package mihon.desktop.library.local

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
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
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    fun `two manga with identical chapter relative paths retain distinct local assets`() {
        val firstSource = createMangaAt(tempDir.resolve("first-manga"), pageContent = "first-page")
        val secondSource = createMangaAt(
            tempDir.resolve("second-manga"),
            pageContent = "second-page-is-different",
            archiveBytes = byteArrayOf(9, 8, 7, 6),
        )
        val mediaRoot = tempDir.resolve("two-manga-media")
        val database = tempDir.resolve("two-manga.db")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            val importer = LocalMangaImporter(repository)

            importer.import(firstSource, mediaRoot, 1)
            importer.import(secondSource, mediaRoot, 2)

            repository.librarySnapshot().map { it.title }.toSet() shouldBe setOf("first-manga", "second-manga")
            queryRows(
                database,
                """
                SELECT manga.title, local_chapter_asset.relative_path
                FROM local_chapter_asset
                JOIN chapter ON chapter.id = local_chapter_asset.chapter_id
                JOIN manga ON manga.id = chapter.manga_id
                ORDER BY manga.title, local_chapter_asset.relative_path
                """.trimIndent(),
            ) shouldBe listOf(
                listOf("first-manga", "第 1 话"),
                listOf("first-manga", "第 2 话.CBZ"),
                listOf("second-manga", "第 1 话"),
                listOf("second-manga", "第 2 话.CBZ"),
            )
            val paths = queryRows(
                database,
                "SELECT manga.title, local_manga_entry.storage_path FROM local_manga_entry " +
                    "JOIN manga ON manga.id = local_manga_entry.manga_id ORDER BY manga.title",
            ).associate { row -> row[0]!! to Path.of(row[1]!!) }
            Files.readString(paths.getValue("first-manga").resolve("第 1 话").resolve("页 01.jpg")) shouldBe
                "first-page"
            Files.readString(paths.getValue("second-manga").resolve("第 1 话").resolve("页 01.jpg")) shouldBe
                "second-page-is-different"
        }
    }

    @Test
    fun `traversal zip is copied as opaque archive metadata without extracting outside media`() {
        val source = Files.createDirectory(tempDir.resolve("opaque-zip-source"))
        ZipOutputStream(Files.newOutputStream(source.resolve("escape.zip"))).use { zip ->
            zip.putNextEntry(ZipEntry("../escaped.txt"))
            zip.write("not extracted".toByteArray())
            zip.closeEntry()
        }
        val mediaRoot = tempDir.resolve("opaque-zip-media")
        val database = tempDir.resolve("opaque-zip.db")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            LocalMangaImporter(repository).import(source, mediaRoot, 1)

            val promoted = Path.of(
                queryRows(database, "SELECT storage_path FROM local_manga_entry").single().single()!!,
            )
            Files.isRegularFile(promoted.resolve("escape.zip")) shouldBe true
            Files.exists(promoted.resolve("escaped.txt")) shouldBe false
            Files.exists(mediaRoot.resolve("escaped.txt")) shouldBe false
            queryRows(database, "SELECT relative_path, asset_kind FROM local_chapter_asset") shouldBe
                listOf(listOf("escape.zip", "ARCHIVE"))
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
    fun `mid-copy and atomic-move faults leave no owned residue or database rows`() {
        listOf("mid-copy", "atomic-move").forEach { failurePoint ->
            val caseRoot = Files.createDirectory(tempDir.resolve("fault-$failurePoint"))
            val source = createMangaAt(
                caseRoot.resolve("source"),
                pageContent = "x".repeat(LOCAL_COPY_TEST_BYTES),
            )
            val mediaRoot = caseRoot.resolve("media")
            val manualPaths = createManualMediaChildren(mediaRoot)
            val database = caseRoot.resolve("library.db")
            DesktopLibraryDatabaseFactory.open(database).use { repository ->
                repository.insertManga(MangaRecord(sourceId = 99, url = "before", title = "before"))
                val before = dumpImportTables(database)
                val faults = object : LocalFileFaults {
                    override fun afterCopyChunk(source: Path, target: Path, copiedBytes: Long) {
                        if (failurePoint == "mid-copy" && copiedBytes > 0) throw IOException("forced mid-copy")
                    }

                    override fun beforeAtomicMove(source: Path, target: Path) {
                        if (failurePoint == "atomic-move") {
                            throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "forced")
                        }
                    }
                }
                val importer = LocalMangaImporter(
                    mutations = repository,
                    stager = LocalImportStager(fileFaults = faults),
                )

                shouldThrowAny { importer.import(source, mediaRoot, 2) }

                dumpImportTables(database) shouldBe before
                assertOnlyManualMediaChildrenRemain(mediaRoot, manualPaths)
            }
        }
    }

    @Test
    fun `real SQL failure after promotion rolls transaction and owned media back`() {
        val source = createManga("sql-failure-source")
        val mediaRoot = tempDir.resolve("sql-failure-media")
        val manualPaths = createManualMediaChildren(mediaRoot)
        val database = tempDir.resolve("sql-failure.db")
        DesktopLibraryDatabaseFactory.open(database).use { repository ->
            repository.insertManga(MangaRecord(sourceId = 99, url = "before", title = "before"))
            executeSql(
                database,
                """
                CREATE TRIGGER reject_local_registration
                BEFORE INSERT ON local_manga_entry
                BEGIN
                  SELECT RAISE(ABORT, 'forced local registration constraint');
                END
                """.trimIndent(),
            )
            val before = dumpImportTables(database)
            var promoted = false
            val checkpoint = object : LocalImportCheckpoint {
                override fun afterPromotion(staged: StagedLocalManga) {
                    promoted = Files.isDirectory(staged.finalPath)
                }
            }

            shouldThrowAny {
                LocalMangaImporter(repository, checkpoint = checkpoint).import(source, mediaRoot, 2)
            }

            promoted shouldBe true
            dumpImportTables(database) shouldBe before
            assertOnlyManualMediaChildrenRemain(mediaRoot, manualPaths)
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
        val existing = mediaRoot.resolve("manga").resolve(FIXED_IMPORT_ID)
        Files.createDirectories(existing)
        Files.writeString(existing.resolve("keep.txt"), "keep")
        val staged = LocalImportStager(idFactory = { FIXED_IMPORT_ID })
            .stage(LocalImportScanner().scan(source), mediaRoot)

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
        val existing = mediaRoot.resolve(".staging").resolve(FIXED_IMPORT_ID)
        Files.createDirectories(existing)
        Files.writeString(existing.resolve("keep.txt"), "keep")

        shouldThrow<LocalImportRejected> {
            LocalImportStager(idFactory = { FIXED_IMPORT_ID }).stage(LocalImportScanner().scan(source), mediaRoot)
        }

        Files.readString(existing.resolve("keep.txt")) shouldBe "keep"
        listChildren(mediaRoot.resolve("manga")).shouldBeEmpty()
    }

    @Test
    fun `orphan cleanup deletes only owned orphans and preserves database and manual paths`() {
        val mediaRoot = tempDir.resolve("cleanup-media")
        val source = createManga("cleanup-source")
        val manifest = LocalImportScanner().scan(source)
        val staging = LocalImportStager(idFactory = { STAGING_ORPHAN_ID }).stage(manifest, mediaRoot)
        val stagingOrphan = staging.stagingPath
        val finalOrphanHandle = LocalImportStager(idFactory = { FINAL_ORPHAN_ID }).stage(manifest, mediaRoot)
        val finalOrphan = finalOrphanHandle.promote()
        val retainedHandle = LocalImportStager(idFactory = { RETAINED_IMPORT_ID }).stage(manifest, mediaRoot)
        val retained = retainedHandle.promote()
        retainedHandle.markCommitted()
        retainedHandle.close()
        val manualPaths = createManualMediaChildren(mediaRoot)
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
            Files.readString(retained.resolve("第 1 话").resolve("页 01.jpg")) shouldBe "page"
            manualPaths.forEach { Files.exists(it) shouldBe true }
        }
        staging.close()
        finalOrphanHandle.close()
    }

    @Test
    fun `staging owns an atomic marker and uses private POSIX permissions when supported`() {
        val source = createManga("private-staging-source")
        val mediaRoot = tempDir.resolve("private-staging-media")
        val staged = LocalImportStager(idFactory = { PRIVATE_IMPORT_ID })
            .stage(LocalImportScanner().scan(source), mediaRoot)

        staged.use {
            val hiddenFiles = Files.list(it.stagingPath).use { entries ->
                entries.filter { entry -> entry.fileName.toString().startsWith('.') && Files.isRegularFile(entry) }
                    .toList()
            }
            hiddenFiles.size shouldBe 1
            Files.readString(hiddenFiles.single()).shouldContain(PRIVATE_IMPORT_ID)
            val attributes = Files.readAttributes(
                it.stagingPath,
                java.nio.file.attribute.BasicFileAttributes::class.java,
                java.nio.file.LinkOption.NOFOLLOW_LINKS,
            )
            isLinkOrReparsePoint(it.stagingPath, attributes) shouldBe false
            val posix = Files.getFileAttributeView(it.stagingPath, PosixFileAttributeView::class.java)
            if (posix != null) {
                posix.readAttributes().permissions() shouldBe setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                )
            }
        }
    }

    @Test
    fun `ownership marker is excluded from exact configured import limits`() {
        val source = createManga("exact-limits-source")
        val scanner = LocalImportScanner(LocalImportLimits(maxChapters = 2, maxEntries = 3, maxBytes = 7))
        val manifest = scanner.scan(source)

        LocalImportStager(scanner = scanner, idFactory = { PRIVATE_IMPORT_ID })
            .stage(manifest, tempDir.resolve("exact-limits-media"))
            .use { staged -> staged.manifest shouldBe manifest }
    }

    @Test
    fun `stager rejects a source ancestor swapped to a link after scanning`() {
        val originalParent = Files.createDirectory(tempDir.resolve("original-parent"))
        val source = createMangaAt(originalParent.resolve("manga"))
        val manifest = LocalImportScanner().scan(source)
        val relocatedParent = tempDir.resolve("relocated-parent")
        Files.move(originalParent, relocatedParent)
        createImporterDirectoryLink(originalParent, relocatedParent) shouldBe true

        val result = runCatching {
            LocalImportStager().stage(manifest, tempDir.resolve("ancestor-swap-media"))
        }
        result.getOrNull()?.close()
        try {
            (result.exceptionOrNull() is LocalImportRejected) shouldBe true
            result.exceptionOrNull()!!.message.shouldContain("ancestor")
            assertNoImportResidue(tempDir.resolve("ancestor-swap-media"))
        } finally {
            Files.deleteIfExists(originalParent)
        }
    }

    private fun createManga(name: String): Path = createMangaAt(tempDir.resolve(name))

    private fun createMangaAt(
        source: Path,
        pageContent: String = "page",
        archiveBytes: ByteArray = byteArrayOf(1, 2, 3),
    ): Path {
        val directoryChapter = source.resolve("第 1 话")
        Files.createDirectories(directoryChapter)
        Files.writeString(directoryChapter.resolve("页 01.jpg"), pageContent)
        Files.write(source.resolve("第 2 话.CBZ"), archiveBytes)
        return source
    }

    private fun assertNoImportResidue(mediaRoot: Path) {
        listChildren(mediaRoot.resolve(".staging")).shouldBeEmpty()
        listChildren(mediaRoot.resolve("manga")).shouldBeEmpty()
    }
}

private const val LOCAL_COPY_TEST_BYTES = 128 * 1024
private const val FIXED_IMPORT_ID = "00000000-0000-4000-8000-000000000001"
private const val STAGING_ORPHAN_ID = "00000000-0000-4000-8000-000000000002"
private const val FINAL_ORPHAN_ID = "00000000-0000-4000-8000-000000000003"
private const val RETAINED_IMPORT_ID = "00000000-0000-4000-8000-000000000004"
private const val PRIVATE_IMPORT_ID = "00000000-0000-4000-8000-000000000005"

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

private fun createManualMediaChildren(mediaRoot: Path): Set<Path> {
    val stagingDirectory = mediaRoot.resolve(".staging").resolve("manual-directory")
    val stagingFile = mediaRoot.resolve(".staging").resolve("manual-file.txt")
    val mangaDirectory = mediaRoot.resolve("manga").resolve("manual-directory")
    val mangaFile = mediaRoot.resolve("manga").resolve("manual-file.txt")
    Files.createDirectories(stagingDirectory)
    Files.createDirectories(mangaDirectory)
    Files.writeString(stagingDirectory.resolve("keep.txt"), "keep")
    Files.writeString(mangaDirectory.resolve("keep.txt"), "keep")
    Files.writeString(stagingFile, "keep")
    Files.writeString(mangaFile, "keep")
    return setOf(stagingDirectory, stagingFile, mangaDirectory, mangaFile)
}

private fun assertOnlyManualMediaChildrenRemain(mediaRoot: Path, manualPaths: Set<Path>) {
    listChildren(mediaRoot.resolve(".staging")).toSet() shouldBe
        manualPaths.filterTo(mutableSetOf()) { it.parent == mediaRoot.resolve(".staging") }
    listChildren(mediaRoot.resolve("manga")).toSet() shouldBe
        manualPaths.filterTo(mutableSetOf()) { it.parent == mediaRoot.resolve("manga") }
    manualPaths.forEach { Files.exists(it) shouldBe true }
}

private fun executeSql(database: Path, sql: String) {
    DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
        connection.createStatement().use { statement -> statement.execute(sql) }
    }
}

private fun createImporterJunction(link: Path, target: Path): Boolean =
    ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
        .redirectErrorStream(true)
        .start()
        .waitFor() == 0

private fun createImporterDirectoryLink(link: Path, target: Path): Boolean =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        createImporterJunction(link, target)
    } else {
        runCatching {
            Files.createSymbolicLink(link, target)
            true
        }.getOrDefault(false)
    }
