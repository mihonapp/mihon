package mihon.desktop.download

import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DownloadAheadTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `checkAndDownloadAhead enqueues next unread chapters`() = runBlocking {
        val downloadDir = tempDir.resolve("downloads")
        val storeFile = tempDir.resolve("downloads.json")
        val diskProvider = DownloadDiskProvider(downloadDir)
        val store = DownloadStore(storeFile)
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = diskProvider,
            networkHelper = DesktopNetworkHelper(),
        )

        try {
            val manga = LibraryManga(
                id = 1L,
                sourceId = 10L,
                url = "/manga/test",
                title = "Test Manga",
                thumbnailUrl = null,
                chapterCount = 4,
                unreadCount = 3,
            )

            val ch1 = createChapter(1L, 1.0, "Chapter 1", read = true)
            val ch2 = createChapter(2L, 2.0, "Chapter 2", read = false)
            val ch3 = createChapter(3L, 3.0, "Chapter 3", read = false)
            val ch4 = createChapter(4L, 4.0, "Chapter 4", read = false)
            val allChapters = listOf(ch1, ch2, ch3, ch4)

            // Read ch1 with count = 2 -> should enqueue ch2 and ch3
            downloader.checkAndDownloadAhead(
                manga = manga,
                currentChapter = ch1,
                allChapters = allChapters,
                count = 2,
                autoStart = false,
            )

            val queue = downloader.queueState.value
            assertEquals(2, queue.size)
            assertEquals("Chapter 2", queue[0].chapterName)
            assertEquals("Chapter 3", queue[1].chapterName)
        } finally {
            downloader.close()
        }
    }

    @Test
    fun `deleteDownloadedChapter removes chapter folder from disk`() {
        val downloadDir = tempDir.resolve("downloads")
        val storeFile = tempDir.resolve("downloads.json")
        val diskProvider = DownloadDiskProvider(downloadDir)
        val store = DownloadStore(storeFile)
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = diskProvider,
            networkHelper = DesktopNetworkHelper(),
        )

        try {
            val manga = LibraryManga(
                id = 1L,
                sourceId = 10L,
                url = "/manga/test",
                title = "Test Manga",
                thumbnailUrl = null,
                chapterCount = 1,
                unreadCount = 0,
            )
            val ch = createChapter(1L, 1.0, "Chapter 1", read = true)

            // Create dummy downloaded chapter dir
            val chDir = diskProvider.getChapterDir(manga.sourceId, manga.title, ch.name)
            Files.createDirectories(chDir)
            Files.writeString(chDir.resolve("001.jpg"), "fake-image")
            assertTrue(diskProvider.isChapterDownloaded(manga.sourceId, manga.title, ch.name))

            val deleted = downloader.deleteDownloadedChapter(manga, ch)
            assertTrue(deleted)
            assertFalse(diskProvider.isChapterDownloaded(manga.sourceId, manga.title, ch.name))
            assertFalse(Files.exists(chDir))
        } finally {
            downloader.close()
        }
    }

    private fun createChapter(id: Long, chapterNumber: Double, name: String, read: Boolean): LibraryChapter {
        return LibraryChapter(
            id = id,
            mangaId = 1L,
            url = "/ch/$id",
            name = name,
            scanlator = null,
            read = read,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = chapterNumber,
            sourceOrder = id,
            lastModifiedAt = 0L,
            version = 1L,
            memoJson = "",
        )
    }
}
