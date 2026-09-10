package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.reader.model.PageId
import mihon.reader.source.ReaderChapterAsset
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Path

class OnlineChapterSourceTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private lateinit var networkHelper: DesktopNetworkHelper

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/page1.jpg") { exchange ->
            val img = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) // JPEG header
            exchange.sendResponseHeaders(200, img.size.toLong())
            exchange.responseBody.use { it.write(img) }
        }
        server.createContext("/protected.webp") { exchange ->
            if (exchange.requestHeaders.getFirst("X-Reader-Token") != "accepted") {
                exchange.sendResponseHeaders(403, -1)
            } else {
                val img = byteArrayOf(
                    'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
                    0, 0, 0, 0,
                    'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
                )
                exchange.sendResponseHeaders(200, img.size.toLong())
                exchange.responseBody.use { it.write(img) }
            }
        }
        server.createContext("/not-an-image") { exchange ->
            val body = "<html>sign in required</html>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        serverPort = server.address.port

        networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("test.ext", listOf("127.0.0.1"))
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `loads pages and streams page image through brokered network helper and caches locally`(
        @TempDir tempDir: Path,
    ) {
        runBlocking {
            val cacheDir = tempDir.resolve("cache").toFile()
            val dummyWorkingDir = tempDir.resolve("work").toFile()
            val dummyManager = WindowsExtensionProcessManager(dummyWorkingDir)

            val asset = ReaderChapterAsset(
                mangaId = 1L,
                chapterId = 100L,
                mangaTitle = "Online Manga",
                chapterName = "Chapter 1",
                storageRoot = tempDir,
                relativePath = Path.of("none"),
                assetKind = "ONLINE",
                sizeBytes = 0L,
                modifiedAt = 0L,
                lastPageRead = 0L,
                read = false,
            )

            val chapter = SChapter(
                url = "/chapter/1",
                name = "Chapter 1",
            )

            val onlineSource = OnlineChapterSource(
                asset = asset,
                sourceId = 1L,
                chapter = chapter,
                processManager = dummyManager,
                networkHelper = networkHelper,
                cacheDir = cacheDir,
            )
            onlineSource.cachedPages =
                listOf(Page(index = 0, imageUrl = "http://127.0.0.1:" + serverPort + "/page1.jpg"))

            val pages = onlineSource.pages()
            pages shouldHaveSize 1
            pages.first().id.chapterId shouldBe "100"

            val input = onlineSource.open(PageId("100", "page_0000.jpg"))
            val bytes = input.input.readBytes()
            bytes.size shouldBe 4
            bytes[0] shouldBe 0xFF.toByte()
            bytes[1] shouldBe 0xD8.toByte()

            // Verify local cache file created
            cacheDir.listFiles()?.isNotEmpty() shouldBe true
        }
    }

    @Test
    fun `forwards source image headers and rejects html response as unsupported image`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val source = testSource(tempDir)
        source.cachedPages = listOf(
            Page(
                index = 0,
                imageUrl = "http://127.0.0.1:$serverPort/protected.webp",
                headers = mapOf("X-Reader-Token" to "accepted"),
            ),
            Page(index = 1, imageUrl = "http://127.0.0.1:$serverPort/not-an-image"),
        )

        source.pages()
        source.open(PageId("100", "page_0000.webp")).use { input ->
            input.input.readBytes().copyOfRange(8, 12).decodeToString() shouldBe "WEBP"
        }
        shouldThrow<ReaderFailure.UnsupportedImage> {
            source.open(PageId("100", "page_0001.bin"))
        }
    }

    private fun testSource(tempDir: Path): OnlineChapterSource {
        val asset = ReaderChapterAsset(
            mangaId = 1L,
            chapterId = 100L,
            mangaTitle = "Online Manga",
            chapterName = "Chapter 1",
            storageRoot = tempDir,
            relativePath = Path.of("none"),
            assetKind = "ONLINE",
            sizeBytes = 0L,
            modifiedAt = 0L,
            lastPageRead = 0L,
            read = false,
        )
        return OnlineChapterSource(
            asset = asset,
            sourceId = 1L,
            chapter = SChapter(url = "/chapter/1", name = "Chapter 1"),
            processManager = WindowsExtensionProcessManager(tempDir.resolve("work").toFile()),
            networkHelper = networkHelper,
            cacheDir = tempDir.resolve("cache").toFile(),
        )
    }
}
