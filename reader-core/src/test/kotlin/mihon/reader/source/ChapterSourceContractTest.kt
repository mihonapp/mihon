package mihon.reader.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.reader.memory.BoundedReaderMemoryBudget
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ChapterSourceContractTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `image entry policy normalizes safe names and rejects traversal`() {
        ImageEntryPolicy.normalize("pages\\001.JPG") shouldBe "pages/001.JPG"
        ImageEntryPolicy.isSupportedImage("pages/001.JPG") shouldBe true
        shouldThrow<ReaderFailure.UnsafePath> {
            ImageEntryPolicy.normalize("../secret.png")
        }
    }

    @Test
    fun `standalone directory and supported containers share open close contract`(): Unit = runBlocking {
        val image = temporaryDirectory.resolve("page.png")
        Files.write(image, pngBytes())
        val directory = Files.createDirectory(temporaryDirectory.resolve("chapter"))
        Files.write(directory.resolve("page.png"), ArchiveFixtures.pageBytes)
        ArchiveFixtures.writeZip(
            temporaryDirectory.resolve("chapter.cbz"),
            listOf("page.png" to ArchiveFixtures.pageBytes),
        )
        ArchiveFixtures.writeTar(
            temporaryDirectory.resolve("chapter.tar"),
            listOf(TarArchiveEntry("page.png") to ArchiveFixtures.pageBytes),
        )
        ArchiveFixtures.writeSevenZ(
            temporaryDirectory.resolve("chapter.7z"),
            listOf("page.png" to ArchiveFixtures.pageBytes),
        )
        TarCompression.entries.filterNot { it == TarCompression.NONE }.forEach { compression ->
            ArchiveFixtures.writeCompressedTar(
                temporaryDirectory.resolve("chapter.${compression.name.lowercase()}"),
                compression,
                listOf(TarArchiveEntry("page.png") to ArchiveFixtures.pageBytes),
            )
        }
        ArchiveFixtures.copyCommittedFixture(temporaryDirectory, "valid-rar4.rar")

        val assets = listOf(
            ArchiveFixtures.asset(temporaryDirectory, "page.png", "IMAGE"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter", "DIRECTORY"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter.cbz"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter.tar"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter.7z"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter.gzip"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter.bzip2"),
            ArchiveFixtures.asset(temporaryDirectory, "chapter.xz"),
            ArchiveFixtures.asset(temporaryDirectory, "valid-rar4.rar"),
        )
        val factory = LocalChapterSourceFactory(BoundedReaderMemoryBudget())
        assets.forEach { asset ->
            val source = factory.create(asset)
            val pages = source.pages()
            pages.map { it.id.entryName }.shouldContainExactly("page.png")
            source.open(pages.single().id).use { input -> input.input.readBytes().isNotEmpty() shouldBe true }
            source.close()
            shouldThrow<ReaderFailure.SourceClosed> { source.pages() }
        }
    }

    @Test
    fun `natural ordering and magic override misleading extension`(): Unit = runBlocking {
        ArchiveFixtures.writeZip(
            temporaryDirectory.resolve("actually-zip.rar"),
            listOf("10.png" to byteArrayOf(10), "2.png" to byteArrayOf(2)),
        )
        LocalChapterSourceFactory().create(
            ArchiveFixtures.asset(temporaryDirectory, "actually-zip.rar"),
        ).use { source ->
            source.pages().map { it.id.entryName }.shouldContainExactly("2.png", "10.png")
        }
    }
}

private fun pngBytes(): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
