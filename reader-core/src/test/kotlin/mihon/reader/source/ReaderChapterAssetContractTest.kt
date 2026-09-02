package mihon.reader.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ReaderChapterAssetContractTest {
    @Test
    fun `chapter asset retains every approved storage field at valid boundaries`() {
        val asset = asset(
            mangaId = 0,
            chapterId = 0,
            mangaTitle = "Manga",
            chapterName = "Chapter",
            storageRoot = Path.of("library"),
            relativePath = Path.of("chapter.cbz"),
            assetKind = "archive",
            sizeBytes = 0,
            modifiedAt = 0,
            lastPageRead = 0,
            read = false,
        )

        asset shouldBe ReaderChapterAsset(
            mangaId = 0,
            chapterId = 0,
            mangaTitle = "Manga",
            chapterName = "Chapter",
            storageRoot = Path.of("library"),
            relativePath = Path.of("chapter.cbz"),
            assetKind = "archive",
            sizeBytes = 0,
            modifiedAt = 0,
            lastPageRead = 0,
            read = false,
        )
    }

    @Test
    fun `chapter asset rejects negative counters and blank storage labels`() {
        val valid = asset()

        shouldThrow<IllegalArgumentException> { valid.copy(mangaId = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(chapterId = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(sizeBytes = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(modifiedAt = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(lastPageRead = -1) }
        shouldThrow<IllegalArgumentException> { valid.copy(mangaTitle = " ") }
        shouldThrow<IllegalArgumentException> { valid.copy(chapterName = " ") }
        shouldThrow<IllegalArgumentException> { valid.copy(assetKind = " ") }
    }

    @Test
    fun `chapter asset preserves path values without resolving escape semantics`() {
        val relativePath = Path.of("..", "untrusted", "chapter.cbz")

        asset(relativePath = relativePath).relativePath shouldBe relativePath
    }

    @Test
    fun `catalog exposes nullable synchronous direct and adjacent lookups`() {
        val previous = asset(chapterId = 1, chapterName = "Previous")
        val current = asset(chapterId = 2, chapterName = "Current")
        val next = asset(chapterId = 3, chapterName = "Next")
        val catalog = object : ReaderChapterCatalog {
            override fun chapterAsset(chapterId: Long): ReaderChapterAsset? =
                listOf(previous, current, next).singleOrNull { it.chapterId == chapterId }

            override fun adjacentReadableChapter(
                chapterId: Long,
                direction: ChapterDirection,
            ): ReaderChapterAsset? = when (chapterId to direction) {
                2L to ChapterDirection.PREVIOUS -> previous
                2L to ChapterDirection.NEXT -> next
                else -> null
            }
        }

        ChapterDirection.entries.shouldContainExactly(ChapterDirection.PREVIOUS, ChapterDirection.NEXT)
        catalog.chapterAsset(2) shouldBe current
        catalog.chapterAsset(99) shouldBe null
        catalog.adjacentReadableChapter(2, ChapterDirection.PREVIOUS) shouldBe previous
        catalog.adjacentReadableChapter(2, ChapterDirection.NEXT) shouldBe next
        catalog.adjacentReadableChapter(1, ChapterDirection.PREVIOUS) shouldBe null
    }
}

private fun asset(
    mangaId: Long = 1,
    chapterId: Long = 2,
    mangaTitle: String = "Manga",
    chapterName: String = "Chapter",
    storageRoot: Path = Path.of("library"),
    relativePath: Path = Path.of("chapter.cbz"),
    assetKind: String = "archive",
    sizeBytes: Long = 1,
    modifiedAt: Long = 1,
    lastPageRead: Long = 0,
    read: Boolean = false,
) = ReaderChapterAsset(
    mangaId = mangaId,
    chapterId = chapterId,
    mangaTitle = mangaTitle,
    chapterName = chapterName,
    storageRoot = storageRoot,
    relativePath = relativePath,
    assetKind = assetKind,
    sizeBytes = sizeBytes,
    modifiedAt = modifiedAt,
    lastPageRead = lastPageRead,
    read = read,
)
