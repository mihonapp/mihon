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
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.extension.source.model.Page
import mihon.reader.model.PageId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import javax.imageio.ImageIO

class OfflineDownloadedChapterReaderTest {

    private fun createSampleImageBytes(): ByteArray {
        val img = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, 20, 20)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        return baos.toByteArray()
    }

    @Test
    fun `downloads chapter and reads completely offline via DesktopReaderFactory`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val dbPath = tempDir.resolve("test.db")
        val repository = DesktopLibraryDatabaseFactory.open(dbPath)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val imageBytes = createSampleImageBytes()

        server.createContext("/p1.png") { ex ->
            ex.sendResponseHeaders(200, imageBytes.size.toLong())
            ex.responseBody.use { it.write(imageBytes) }
        }
        server.createContext("/p2.png") { ex ->
            ex.sendResponseHeaders(200, imageBytes.size.toLong())
            ex.responseBody.use { it.write(imageBytes) }
        }
        server.start()
        val port = server.address.port

        val networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("test.ext", listOf("127.0.0.1", "localhost"))

        val store = DownloadStore(tempDir.resolve("downloads.json"))
        val diskProvider = DownloadDiskProvider(tempDir.resolve("downloads"))

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
        )

        // 1. Insert manga and chapter into database
        val mangaId = repository.insertManga(
            MangaRecord(
                sourceId = 999L,
                url = "/online/manga/1",
                title = "Offline Read Manga",
            ),
        )
        val chapterId = repository.insertChapter(
            ChapterRecord(
                mangaId = mangaId,
                url = "/online/chapter/10",
                name = "Chapter 10",
            ),
        )

        val manga = LibraryManga(
            id = mangaId,
            sourceId = 999L,
            url = "/online/manga/1",
            title = "Offline Read Manga",
            thumbnailUrl = null,
            chapterCount = 1L,
            unreadCount = 1L,
            author = null,
        )
        val chapter = LibraryChapter(
            id = chapterId,
            mangaId = mangaId,
            url = "/online/chapter/10",
            name = "Chapter 10",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            chapterNumber = 10.0,
            sourceOrder = 0L,
            lastModifiedAt = 0L,
            version = 0L,
            memoJson = "{}",
        )

        // 2. Enqueue download
        downloader.enqueue(manga, listOf(chapter), autoStart = true)

        withTimeout(5000) {
            while (downloader.queueState.value.firstOrNull()?.status != DownloadStatus.COMPLETED) {
                delay(50)
            }
        }

        // 3. Verify download finished and asset registered in repository
        val asset = repository.chapterAsset(chapterId)
        asset shouldNotBe null
        asset!!.mangaTitle shouldBe "Offline Read Manga"
        asset.chapterName shouldBe "Chapter 10"

        // 4. Shut down HTTP server to guarantee zero network connectivity
        server.stop(0)

        // 5. Open in DesktopReaderFactory
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

        // 6. Decode page frame offline
        val frame = readerFactory.loadFrame(firstPage.id, 0)
        frame.metadata.width shouldBe 20
        frame.metadata.height shouldBe 20
        frame.tile.image.width shouldBe 20
        frame.tile.image.height shouldBe 20

        repository.close()
    }
}
