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

        val page1Bytes = byteArrayOf(1, 2, 3, 4)
        val page2Bytes = byteArrayOf(5, 6, 7, 8)

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
            override fun linkCategory(mangaId: Long, categoryId: Long) = Unit
            override fun upsertHistory(value: HistoryRecord) = Unit
            override fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord? = null
            override fun insertTracking(value: TrackingRecord) = Unit
            override fun updateTracking(value: TrackingRecord) = Unit
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
        insertedChapters[0].sizeBytes shouldBe 8L
        insertedChapters[0].assetKind shouldBe "DIRECTORY"

        provider.isChapterDownloaded(sourceId, mangaTitle, chapterName) shouldBe true
    }

    @Test
    fun `finalizeChapter fails if any page is missing`(@TempDir tempDir: Path) {
        val provider = DownloadDiskProvider(tempDir)
        val tempChapterDir = provider.getTempChapterDir(1L, "Manga", "Ch1")
        provider.savePage(tempChapterDir, 0, byteArrayOf(1, 2, 3))
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
