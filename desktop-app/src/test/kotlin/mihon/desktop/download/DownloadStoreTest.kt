package mihon.desktop.download

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DownloadStoreTest {

    @Test
    fun `save and restore queue successfully`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)

        val queue = listOf(
            DesktopDownload(
                chapterId = 101L,
                mangaId = 1L,
                sourceId = 10L,
                mangaTitle = "One Piece",
                chapterName = "Chapter 1000",
                chapterUrl = "/ch1000",
                pages = listOf(
                    DownloadPage(index = 0, url = "https://example.com/0.jpg", status = PageStatus.READY),
                    DownloadPage(index = 1, url = "https://example.com/1.jpg", status = PageStatus.QUEUE),
                ),
                status = DownloadStatus.QUEUED,
                progress = 0.5f,
            ),
        )

        store.save(queue)
        val restored = store.restore()

        restored shouldHaveSize 1
        val item = restored.first()
        item.chapterId shouldBe 101L
        item.mangaTitle shouldBe "One Piece"
        item.chapterName shouldBe "Chapter 1000"
        item.status shouldBe DownloadStatus.QUEUED
        item.pages shouldHaveSize 2
        item.downloadedImages shouldBe 1
    }

    @Test
    fun `resets DOWNLOADING status to QUEUED on restore for crash recovery`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)

        val queue = listOf(
            DesktopDownload(
                chapterId = 202L,
                mangaId = 2L,
                sourceId = 10L,
                mangaTitle = "Bleach",
                chapterName = "Chapter 1",
                chapterUrl = "/ch1",
                status = DownloadStatus.DOWNLOADING,
            ),
        )

        store.save(queue)
        val restored = store.restore()

        restored shouldHaveSize 1
        restored.first().status shouldBe DownloadStatus.QUEUED
    }

    @Test
    fun `restore non-existent file returns empty list`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("missing.json")
        val store = DownloadStore(storeFile)

        store.restore() shouldBe emptyList()
    }
}
