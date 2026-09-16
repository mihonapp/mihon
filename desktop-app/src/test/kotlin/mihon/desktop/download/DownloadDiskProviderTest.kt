package mihon.desktop.download

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DownloadDiskProviderTest {

    @Test
    fun `custom root still discovers completed chapters in the previous default root`(@TempDir tempDir: Path) {
        val oldRoot = tempDir.resolve("old-downloads")
        val activeRoot = tempDir.resolve("new-downloads")
        val sourceId = 42L
        val mangaTitle = "Moved Manga"
        val chapterName = "Chapter 1"
        val oldProvider = DownloadDiskProvider(oldRoot)
        val temporary = oldProvider.getTempChapterDir(sourceId, mangaTitle, chapterName)
        oldProvider.savePage(temporary, 0, validDownloadImage())
        val published = oldProvider.finalizeChapter(
            sourceId = sourceId,
            mangaId = 100L,
            chapterId = 200L,
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            totalPages = 1,
        )

        val provider = DownloadDiskProvider(activeRoot, legacyDownloadsDirs = listOf(oldRoot))

        provider.isChapterDownloaded(sourceId, mangaTitle, chapterName) shouldBe true
        provider.findChapterDir(sourceId, mangaTitle, chapterName) shouldBe published
        provider.getTempChapterDir(sourceId, mangaTitle, "Chapter 2").startsWith(activeRoot) shouldBe true
    }

    @Test
    fun `sanitizes illegal Windows characters in file and directory names`(@TempDir tempDir: Path) {
        val provider = DownloadDiskProvider(tempDir)
        val sanitized = provider.sanitizeFileName("Manga: Volume 1 / Part 2? *Special* <Final>")
        sanitized shouldBe "Manga_ Volume 1 _ Part 2_ _Special_ _Final_"
    }

    @Test
    fun `saves pages and atomically finalizes chapter`(@TempDir tempDir: Path) {
        val provider = DownloadDiskProvider(tempDir)
        val sourceId = 42L
        val mangaId = 100L
        val chapterId = 200L
        val mangaTitle = "Test Manga"
        val chapterName = "Chapter 1"

        val tempChapterDir = provider.getTempChapterDir(sourceId, mangaTitle, chapterName)
        tempChapterDir.fileName.toString() shouldEndWith "_tmp"

        val page1Bytes = validDownloadImage()
        val page2Bytes = validDownloadImage()

        provider.savePage(tempChapterDir, 0, page1Bytes)
        provider.savePage(tempChapterDir, 1, page2Bytes)

        val insertedManga = mutableListOf<LocalMangaRecord>()
        val insertedChapters = mutableListOf<LocalChapterRecord>()

        val fakeMutationPort = object : LibraryMutationPort {
            override fun <T> transaction(block: LibraryMutationPort.() -> T): T = block()
            override fun findManga(sourceId: Long, url: String): MangaRecord? = null
            override fun insertManga(value: MangaRecord): Long = 1L
            override fun updateManga(value: MangaRecord) = Unit
            override fun findChapter(mangaId: Long, url: String): ChapterRecord? = null
            override fun insertChapter(value: ChapterRecord): Long = 1L
            override fun updateChapter(value: ChapterRecord) = Unit
            override fun upsertCategory(value: CategoryRecord): Long = 1L
            override fun deleteCategory(categoryId: Long) = Unit
            override fun updateCategoryName(categoryId: Long, name: String) = Unit
            override fun updateCategoryOrder(categoryId: Long, sortOrder: Long) = Unit
            override fun linkCategory(mangaId: Long, categoryId: Long) = Unit
            override fun unlinkCategory(mangaId: Long, categoryId: Long) = Unit
            override fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) = Unit
            override fun upsertHistory(value: HistoryRecord) = Unit
            override fun deleteHistory(chapterId: Long) = Unit
            override fun clearAllHistory() = Unit
            override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? = null
            override fun insertTracking(value: TrackingRecord) = Unit
            override fun updateTracking(value: TrackingRecord) = Unit
            override fun deleteTracking(mangaId: Long, trackerId: Long) = Unit
            override fun upsertSource(value: SourceRecord) = Unit
            override fun upsertPreference(value: PreferenceSnapshotRecord) = Unit
            override fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord) = Unit
            override fun findLocalMangaByManifest(manifestSha256: String): LocalMangaRecord? = null
            override fun localMangaStoragePaths(): Set<String> = emptySet()
            override fun insertLocalManga(value: LocalMangaRecord) {
                insertedManga.add(value)
            }
            override fun insertLocalChapter(value: LocalChapterRecord) {
                insertedChapters.add(value)
            }
            override fun insertReport(value: ImportReportRecord): Long = 1L
            override fun insertReportItem(reportId: Long, value: ImportReportItemRecord) = Unit
        }

        val targetDir = provider.finalizeChapter(
            sourceId = sourceId,
            mangaId = mangaId,
            chapterId = chapterId,
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            totalPages = 2,
            mutationPort = fakeMutationPort,
        )

        Files.exists(tempChapterDir) shouldBe false
        Files.exists(targetDir) shouldBe true
        Files.exists(targetDir.resolve("001.jpg")) shouldBe true
        Files.exists(targetDir.resolve("002.jpg")) shouldBe true

        insertedManga.size shouldBe 1
        insertedManga[0].mangaId shouldBe mangaId
        insertedChapters.size shouldBe 1
        insertedChapters[0].chapterId shouldBe chapterId
        insertedChapters[0].sizeBytes shouldBe (page1Bytes.size + page2Bytes.size).toLong()
        insertedChapters[0].assetKind shouldBe "DIRECTORY"

        provider.isChapterDownloaded(sourceId, mangaTitle, chapterName) shouldBe true
    }

    @Test
    fun `published chapter stops reporting downloaded when a page is corrupted`(@TempDir tempDir: Path) {
        val provider = DownloadDiskProvider(tempDir)
        val sourceId = 42L
        val mangaTitle = "Integrity Manga"
        val chapterName = "Chapter 1"
        val tempChapterDir = provider.getTempChapterDir(sourceId, mangaTitle, chapterName)
        provider.savePage(tempChapterDir, 0, validDownloadImage())
        val targetDir = provider.finalizeChapter(
            sourceId = sourceId,
            mangaId = 100L,
            chapterId = 200L,
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            totalPages = 1,
        )

        provider.isChapterDownloaded(sourceId, mangaTitle, chapterName) shouldBe true
        Files.writeString(targetDir.resolve("001.jpg"), "not-an-image")

        provider.isChapterDownloaded(sourceId, mangaTitle, chapterName) shouldBe false
        provider.isChapterDownloaded(sourceId, mangaTitle, chapterName) shouldBe false
    }

    @Test
    fun `finalizeChapter fails if any page is missing`(@TempDir tempDir: Path) {
        val provider = DownloadDiskProvider(tempDir)
        val tempChapterDir = provider.getTempChapterDir(1L, "Manga", "Ch1")
        provider.savePage(tempChapterDir, 0, validDownloadImage())
        // Page 1 (index 1) is missing, but totalPages is 2

        shouldThrow<IOException> {
            provider.finalizeChapter(
                sourceId = 1L,
                mangaId = 1L,
                chapterId = 1L,
                mangaTitle = "Manga",
                chapterName = "Ch1",
                totalPages = 2,
            )
        }
    }
}
