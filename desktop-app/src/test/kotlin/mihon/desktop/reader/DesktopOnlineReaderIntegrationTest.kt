package mihon.desktop.reader

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import javax.imageio.ImageIO

class DesktopOnlineReaderIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `online chapter opens through extension source without a local download`(): Unit = runBlocking {
        val imageBytes = pngBytes(30, 30)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/page.png") { exchange ->
            exchange.sendResponseHeaders(200, imageBytes.size.toLong())
            exchange.responseBody.use { it.write(imageBytes) }
        }
        server.start()

        val networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("test.ext", listOf("127.0.0.1"))
        val sourceManager = DesktopSourceManager()
        val sourceId = 424242L
        sourceManager.registerBuiltinSource(
            object : WindowsCatalogueSource {
                override val id: Long = sourceId
                override val name: String = "Test source"
                override val lang: String = "en"

                override suspend fun getPopularManga(page: Int): MangasPage = error("Not used")
                override suspend fun getLatestUpdates(page: Int): MangasPage = error("Not used")
                override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage =
                    error("Not used")

                override suspend fun getMangaDetails(manga: SManga): SManga = manga
                override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
                override suspend fun getPageList(chapter: SChapter): List<Page> =
                    listOf(Page(index = 0, imageUrl = "http://127.0.0.1:${server.address.port}/page.png"))
            },
        )

        val repository = DesktopLibraryDatabaseFactory.open(tempDir.resolve("library.db"))
        val mangaId = repository.insertManga(
            MangaRecord(sourceId = sourceId, url = "/manga/online", title = "Online Manga"),
        )
        val chapterId = repository.insertChapter(
            ChapterRecord(
                mangaId = mangaId,
                url = "/chapter/1",
                name = "Chapter 1",
                sourceOrder = 1,
                chapterNumber = 1.0,
            ),
        )
        val secondChapterId = repository.insertChapter(
            ChapterRecord(
                mangaId = mangaId,
                url = "/chapter/2",
                name = "Chapter 2",
                sourceOrder = 0,
                chapterNumber = 2.0,
            ),
        )

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val factory = DesktopReaderFactory(
            applicationScope = applicationScope,
            library = repository,
            settings = DesktopReaderSettingsStore(DesktopPreferenceStore(tempDir.resolve("prefs.properties"))),
            onlineChapters = repository,
            sourceManager = sourceManager,
            networkHelper = networkHelper,
            onlineCacheDir = tempDir.resolve("reader-cache").toFile(),
        )
        val session = factory.createSession()
        try {
            session.open(chapterId)
            withTimeout(10_000) {
                session.state.first { it.loadState is ReaderLoadState.Ready }
            }

            val state = session.state.value
            state.pages shouldHaveSize 1
            val frame = factory.loadFrame(state.pages.single().id, frameIndex = 0)
            frame.metadata.width shouldBe 30
            frame.metadata.height shouldBe 30
            frame.tile.image.width shouldBe 30

            session.dispatch(ReaderAction.Next)
            val transitioned = withTimeout(10_000) {
                session.state.first {
                    it.chapterId == secondChapterId && it.loadState is ReaderLoadState.Ready
                }
            }
            transitioned.chapterId shouldBe secondChapterId
        } finally {
            session.closeAndFlush()
            factory.shutdown()
            factory.closeServices()
            sourceManager.close()
            repository.close()
            server.stop(0)
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
