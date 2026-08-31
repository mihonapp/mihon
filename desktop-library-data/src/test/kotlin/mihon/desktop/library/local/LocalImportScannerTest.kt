package mihon.desktop.library.local

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class LocalImportScannerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scanner preserves supported Unicode archive and long directory chapter paths in code point order`() {
        val manga = tempDir.resolve("漫画").resolve("作者").resolve("作品")
        Files.createDirectories(manga)
        val longChapter = (1..5).fold(manga.resolve("长章节")) { path, index ->
            path.resolve("$index-${"x".repeat(55)}")
        }
        Files.createDirectories(longChapter)
        Files.writeString(longChapter.resolve("页 01.jpg"), "page")
        val archiveNames = listOf(
            "第 09 话.CBZ",
            "第 08 话.zip",
            "第 07 话.RaR",
            "第 06 话.cbr",
            "第 05 话.7Z",
            "第 04 话.cb7",
            "第 03 话.TAR",
            "第 02 话.cbt",
            "第 01 话.EpUb",
            "\uE000.cbz",
            "\uD800\uDC00.cbz",
        )
        archiveNames.forEach { Files.write(manga.resolve(it), byteArrayOf(1, 2, 3)) }

        val manifest = LocalImportScanner().scan(manga)

        manifest.title shouldBe "作品"
        manifest.sourceRoot shouldBe manga.toAbsolutePath().normalize()
        manifest.sha256.length shouldBe 64
        manifest.chapters.map { it.relativePath.toString() }.shouldContainExactly(
            (archiveNames + "长章节")
                .map { Path.of(it) }
                .sortedWith { first, second -> compareUnicodeCodePoints(first.toString(), second.toString()) }
                .map(Path::toString),
        )
        manifest.chapters.single { it.name == "长章节" }.run {
            kind shouldBe LocalChapterKind.DIRECTORY
            relativePath shouldBe Path.of("长章节")
            sizeBytes shouldBe 4L
        }
        manifest.chapters.filter { it.name != "长章节" }.forEach {
            it.kind shouldBe LocalChapterKind.ARCHIVE
        }
        longChapter.toString().length.run {
            (this > 260) shouldBe true
        }
    }

    @Test
    fun `hidden supported entries are classified and hidden unsupported entries fail closed`() {
        val manga = Files.createDirectory(tempDir.resolve("hidden-supported"))
        Files.writeString(manga.resolve(".chapter.cbz"), "archive")
        val hiddenDirectory = Files.createDirectory(manga.resolve(".chapter-directory"))
        Files.writeString(hiddenDirectory.resolve("page.jpg"), "page")
        val expectedNames = mutableSetOf(".chapter.cbz", ".chapter-directory")
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val dosHiddenArchive = Files.writeString(manga.resolve("dos-hidden.zip"), "archive")
            Files.setAttribute(dosHiddenArchive, "dos:hidden", true)
            expectedNames += dosHiddenArchive.fileName.toString()
        }

        val manifest = LocalImportScanner().scan(manga)

        manifest.chapters.mapTo(mutableSetOf()) { it.name } shouldBe expectedNames
        manifest.chapters.single { it.name == ".chapter.cbz" }.kind shouldBe LocalChapterKind.ARCHIVE
        manifest.chapters.single { it.name == ".chapter-directory" }.kind shouldBe LocalChapterKind.DIRECTORY

        val unsupported = Files.createDirectory(tempDir.resolve("hidden-unsupported"))
        Files.writeString(unsupported.resolve(".notes.txt"), "unsupported")
        shouldThrow<LocalImportRejected> { LocalImportScanner().scan(unsupported) }
            .message.shouldContain("unsupported top-level file")

        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val dosUnsupported = Files.createDirectory(tempDir.resolve("dos-hidden-unsupported"))
            val hiddenText = Files.writeString(dosUnsupported.resolve("notes.txt"), "unsupported")
            Files.setAttribute(hiddenText, "dos:hidden", true)
            shouldThrow<LocalImportRejected> { LocalImportScanner().scan(dosUnsupported) }
                .message.shouldContain("unsupported top-level file")
        }

        val reservedLooking = Files.createDirectory(tempDir.resolve("source-reserved-looking"))
        Files.writeString(reservedLooking.resolve(".mihon-local-import-owner"), "not internal here")
        shouldThrow<LocalImportRejected> { LocalImportScanner().scan(reservedLooking) }
            .message.shouldContain("unsupported top-level file")
    }

    @Test
    fun `scanner rejects missing roots special entries links and Windows junctions without following them`() {
        shouldThrow<LocalImportRejected> { LocalImportScanner().scan(tempDir.resolve("missing")) }
            .message.shouldContain("readable directory")

        val manga = Files.createDirectory(tempDir.resolve("links"))
        Files.write(manga.resolve("chapter.cbz"), byteArrayOf(1))
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        Files.writeString(outside.resolve("secret.txt"), "secret")
        val link = manga.resolve(".linked-chapter")
        var linkCreated = runCatching {
            Files.createSymbolicLink(link, outside)
            true
        }.getOrDefault(false)
        if (!linkCreated && System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            linkCreated = createJunction(link, outside)
        }
        linkCreated shouldBe true
        shouldThrow<LocalImportRejected> { LocalImportScanner().scan(manga) }
            .message.shouldContain("link or reparse point")
        Files.deleteIfExists(link)

        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val junction = manga.resolve("junction")
            if (createJunction(junction, outside)) {
                try {
                    shouldThrow<LocalImportRejected> { LocalImportScanner().scan(manga) }
                        .message.shouldContain("link or reparse point")
                } finally {
                    Files.deleteIfExists(junction)
                }
            }
        }
    }

    @Test
    fun `scanner rejects a normal manga directory reached through a junction parent`() {
        val realParent = Files.createDirectory(tempDir.resolve("real-parent"))
        val manga = Files.createDirectory(realParent.resolve("manga"))
        Files.write(manga.resolve("chapter.cbz"), byteArrayOf(1))
        val linkedParent = tempDir.resolve("linked-parent")
        createDirectoryLink(linkedParent, realParent) shouldBe true

        try {
            shouldThrow<LocalImportRejected> { LocalImportScanner().scan(linkedParent.resolve("manga")) }
                .message.shouldContain("ancestor")
        } finally {
            Files.deleteIfExists(linkedParent)
        }
    }

    @Test
    fun `candidate validation rejects traversal absolute paths and case folded collisions`() {
        val root = Files.createDirectory(tempDir.resolve("candidate-root")).toAbsolutePath().normalize()
        val scanner = LocalImportScanner()

        shouldThrow<LocalImportRejected> {
            scanner.validateCandidatePaths(root, listOf(Path.of("chapter"), Path.of("..", "escape.cbz")))
        }.message.shouldContain("escapes source root")
        shouldThrow<LocalImportRejected> {
            scanner.validateCandidatePaths(root, listOf(tempDir.resolve("absolute.cbz")))
        }.message.shouldContain("absolute")
        shouldThrow<LocalImportRejected> {
            scanner.validateCandidatePaths(root, listOf(Path.of("Chapter.cbz"), Path.of("chapter.CBZ")))
        }.message.shouldContain("case-folded duplicate")
    }

    @Test
    fun `configured chapter entry and byte limits fail closed`() {
        val manga = Files.createDirectory(tempDir.resolve("limits"))
        Files.write(manga.resolve("one.cbz"), byteArrayOf(1, 2))
        Files.write(manga.resolve("two.cbz"), byteArrayOf(3, 4))

        shouldThrow<LocalImportRejected> {
            LocalImportScanner(LocalImportLimits(maxChapters = 1, maxEntries = 10, maxBytes = 100)).scan(manga)
        }.message.shouldContain("chapter limit")
        shouldThrow<LocalImportRejected> {
            LocalImportScanner(LocalImportLimits(maxChapters = 10, maxEntries = 10, maxBytes = 3)).scan(manga)
        }.message.shouldContain("byte limit")

        val directoryManga = Files.createDirectory(tempDir.resolve("entry-limits"))
        val chapter = Files.createDirectory(directoryManga.resolve("chapter"))
        Files.writeString(chapter.resolve("one.jpg"), "1", StandardOpenOption.CREATE_NEW)
        Files.writeString(chapter.resolve("two.jpg"), "2", StandardOpenOption.CREATE_NEW)
        shouldThrow<LocalImportRejected> {
            LocalImportScanner(LocalImportLimits(maxChapters = 10, maxEntries = 2, maxBytes = 100))
                .scan(directoryManga)
        }.message.shouldContain("entry limit")
    }

    @Test
    fun `deterministic entry faults reject unreadable and ordinary nonregular inputs`() {
        listOf(
            LocalEntryFault.UNREADABLE to "unreadable",
            LocalEntryFault.NONREGULAR to "not a regular file or directory",
        ).forEach { (fault, expectedMessage) ->
            val manga = Files.createDirectory(tempDir.resolve("fault-${fault.name.lowercase()}"))
            Files.write(manga.resolve("chapter.cbz"), byteArrayOf(1))
            val scanner = LocalImportScanner(
                entryFaults = LocalScannerEntryFaults { path ->
                    if (path.fileName.toString() == "chapter.cbz") fault else null
                },
            )

            shouldThrow<LocalImportRejected> { scanner.scan(manga) }
                .message.shouldContain(expectedMessage)
        }
    }

    @Test
    fun `supported DOS reparse attribute read failure rejects the path`() {
        val manga = Files.createDirectory(tempDir.resolve("dos-read-failure"))
        Files.write(manga.resolve("chapter.cbz"), byteArrayOf(1))
        val scanner = LocalImportScanner(
            dosReparseReader = LocalDosReparseReader { throw IOException("forced DOS attribute read failure") },
        )

        shouldThrow<LocalImportRejected> { scanner.scan(manga) }
            .message.shouldContain("DOS reparse attribute")
    }
}

private fun createJunction(link: Path, target: Path): Boolean =
    ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
        .redirectErrorStream(true)
        .start()
        .waitFor() == 0

private fun createDirectoryLink(link: Path, target: Path): Boolean =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        createJunction(link, target)
    } else {
        runCatching {
            Files.createSymbolicLink(link, target)
            true
        }.getOrDefault(false)
    }

private fun compareUnicodeCodePoints(left: String, right: String): Int {
    var leftIndex = 0
    var rightIndex = 0
    while (leftIndex < left.length && rightIndex < right.length) {
        val leftPoint = left.codePointAt(leftIndex)
        val rightPoint = right.codePointAt(rightIndex)
        if (leftPoint != rightPoint) return leftPoint.compareTo(rightPoint)
        leftIndex += Character.charCount(leftPoint)
        rightIndex += Character.charCount(rightPoint)
    }
    return (left.length - leftIndex).compareTo(right.length - rightIndex)
}
