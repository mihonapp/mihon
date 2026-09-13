package mihon.reader.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.reader.memory.BoundedReaderMemoryBudget
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
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
        val directImageOpf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest><item id="image" href="images/2.png" media-type="image/png"/></manifest>
              <spine><itemref idref="image"/></spine>
            </package>"""
        ArchiveFixtures.writeEpub(temporaryDirectory.resolve("chapter.epub"), opf = directImageOpf)

        val assets = listOf(
            ArchiveFixtures.asset(temporaryDirectory, "page.png", "IMAGE") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter", "DIRECTORY") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.cbz") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.tar") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.7z") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.gzip") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.bzip2") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.xz") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "valid-rar4.rar") to "page.png",
            ArchiveFixtures.asset(temporaryDirectory, "chapter.epub") to "OPS/images/2.png",
        )
        val factory = LocalChapterSourceFactory(BoundedReaderMemoryBudget())
        assets.forEach { (asset, expectedPage) ->
            val source = factory.create(asset)
            val pages = source.pages()
            pages.map { it.id.entryName }.shouldContainExactly(expectedPage)
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

    @Test
    fun `concurrent seven zip page opens wait for the exclusive decoder allowance`() = runTest {
        ArchiveFixtures.writeSevenZ(
            temporaryDirectory.resolve("chapter.7z"),
            listOf("1.png" to ArchiveFixtures.pageBytes, "2.png" to ArchiveFixtures.pageBytes),
        )
        val budget = BoundedReaderMemoryBudget()
        val source = SevenZipChapterSource(
            ArchiveFixtures.asset(temporaryDirectory, "chapter.7z"),
            budget,
        )
        val pages = source.pages()
        val first = source.open(pages[0].id)
        val second = async { source.open(pages[1].id) }

        runCurrent()
        second.isCompleted shouldBe false
        first.close()

        second.await().use { it.input.readBytes().isNotEmpty() shouldBe true }
        source.close()
        budget.metrics.reservedBytes shouldBe 0
        budget.close()
    }
}

private fun pngBytes(): ByteArray =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
