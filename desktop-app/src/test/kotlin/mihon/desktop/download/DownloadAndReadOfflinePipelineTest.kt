package mihon.desktop.download

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.notification.WindowsDesktopNotificationService
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.updates.DesktopLibraryUpdateService
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class DownloadAndReadOfflinePipelineTest {

    private fun createSampleImageBytes(color: Color): ByteArray {
        val img = BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, 30, 30)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        return baos.toByteArray()
    }

    @Test
    fun `complete pipeline download chapter, read offline, and check updates`(@TempDir tempDir: Path) = runBlocking {
        // Step 1: Initialize Database and Notification Service
        val dbPath = tempDir.resolve("e2e-pipeline.db")
        val repository = DesktopLibraryDatabaseFactory.open(dbPath)
        val notificationService = WindowsDesktopNotificationService()

        // Step 2: Set up Mock HTTP Server with two pages
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val page1Bytes = createSampleImageBytes(Color.BLUE)
        val page2Bytes = createSampleImageBytes(Color.GREEN)

        server.createContext("/p1.png") { ex ->
            ex.sendResponseHeaders(200, page1Bytes.size.toLong())
            ex.responseBody.use { it.write(page1Bytes) }
        }
        server.createContext("/p2.png") { ex ->
            ex.sendResponseHeaders(200, page2Bytes.size.toLong())
            ex.responseBody.use { it.write(page2Bytes) }
        }
        server.start()
        val port = server.address.port

        val networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("pipeline.ext", listOf("127.0.0.1", "localhost"))

        val store = DownloadStore(tempDir.resolve("downloads.json"))
        val downloadsDir = tempDir.resolve("downloads")
        val diskProvider = DownloadDiskProvider(downloadsDir)

        var completedDownloadNotification = false
        val downloader = DesktopDownloader(
            store = store,
            diskProvider = diskProvider,
            networkHelper = networkHelper,
            mutationPort = repository,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, "http://127.0.0.1:$port/p1.png", "http://127.0.0.1:$port/p1.png"),
                    Page(1, "http://127.0.0.1:$port/p2.png", "http://127.0.0.1:$port/p2.png"),
                )
            },
            onDownloadCompleted = {
                notificationService.notifyDownloadComplete(it.mangaTitle, it.chapterName)
                completedDownloadNotification = true
            },
        )

        // Step 3: Insert Online Manga & Chapter
        val mangaId = repository.insertManga(
            MangaRecord(
                sourceId = 555L,
                url = "/manga/e2e",
                title = "E2E Pipeline Manga",
            ),
        )
        val chapterId = repository.insertChapter(
            ChapterRecord(
                mangaId = mangaId,
                url = "/manga/e2e/ch1",
                name = "Chapter 1",
            ),
        )

        val manga = LibraryManga(
            id = mangaId,
            sourceId = 555L,
            url = "/manga/e2e",
            title = "E2E Pipeline Manga",
            thumbnailUrl = null,
            chapterCount = 1L,
            unreadCount = 1L,
            author = null,
        )
        val chapter = LibraryChapter(
            id = chapterId,
            mangaId = mangaId,
            url = "/manga/e2e/ch1",
            name = "Chapter 1",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = 1.0,
            sourceOrder = 0L,
            lastModifiedAt = 0L,
            version = 0L,
            memoJson = "{}",
        )

        // Step 4: Enqueue download and await completion
        downloader.enqueue(manga, listOf(chapter), autoStart = true)

        withTimeout(5000) {
            while (!completedDownloadNotification || downloader.queueState.value.firstOrNull()?.status != DownloadStatus.COMPLETED) {
                delay(50)
            }
        }

        completedDownloadNotification shouldBe true
        val downloadedItem = downloader.queueState.value.first()
        downloadedItem.status shouldBe DownloadStatus.COMPLETED
        downloadedItem.progress shouldBe 1.0f

        val finalDir = diskProvider.getChapterDir(manga.sourceId, manga.title, chapter.name)
        Files.exists(finalDir) shouldBe true
        Files.exists(finalDir.resolve("001.jpg")) shouldBe true
        Files.exists(finalDir.resolve("002.jpg")) shouldBe true

        // Step 5: Shut down network server -> Simulate 100% offline environment
        server.stop(0)

        // Step 6: Verify offline reading via DesktopReaderFactory
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.json"))
        val settingsStore = DesktopReaderSettingsStore(prefStore)
        val readerFactory = DesktopReaderFactory(appScope, repository, settingsStore)

        val session = readerFactory.createSession()
        session.open(chapterId)

        withTimeout(5000) {
            while (session.state.value.pages.isEmpty()) {
                delay(50)
            }
        }

        session.state.value.pages shouldHaveSize 2
        val firstPage = session.state.value.pages.first()
        val frame = readerFactory.loadFrame(firstPage.id, 0)
        frame.metadata.width shouldBe 30
        frame.metadata.height shouldBe 30
        frame.tile.image.width shouldBe 30

        // Step 7: Test Library Updates check
        var updateNotificationDispatched = false
        val updateService = DesktopLibraryUpdateService(
            repository = repository,
            mutationPort = repository,
            chapterListFetcher = { _, _ ->
                listOf(
                    SChapter(url = "/manga/e2e/ch1", name = "Chapter 1"),
                    SChapter(url = "/manga/e2e/ch2", name = "Chapter 2", chapterNumber = 2.0f),
                )
            },
            onUpdateCompleted = { result ->
                if (result.newChaptersFound > 0) {
                    notificationService.notifyLibraryUpdate(result.newChaptersFound, result.mangaWithNewChapters)
                    updateNotificationDispatched = true
                }
            },
        )

        val updateResult = updateService.updateLibrary()
        updateResult.newChaptersFound shouldBe 1
        updateNotificationDispatched shouldBe true

        val allChapters = repository.chapterSnapshot(mangaId)
        allChapters shouldHaveSize 2
        allChapters.any { it.name == "Chapter 2" } shouldBe true

        notificationService.recentNotifications.value.size shouldBe 2 // 1 download + 1 update

        downloader.close()
        repository.close()
    }
}
