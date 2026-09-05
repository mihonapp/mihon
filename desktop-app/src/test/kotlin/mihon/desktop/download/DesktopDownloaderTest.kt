package mihon.desktop.download

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.HistoryRecord
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.model.PreferenceSnapshotRecord
import mihon.desktop.library.model.SourcePreferenceSnapshotRecord
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.library.model.TrackingRecord
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.extension.source.model.Page
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesktopDownloaderTest {

    private lateinit var server: HttpServer
    private lateinit var networkHelper: DesktopNetworkHelper
    private var serverPort: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        serverPort = server.address.port
        networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("test.ext", listOf("127.0.0.1", "localhost"))
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun createManga(id: Long = 1L, sourceId: Long = 100L, title: String = "Test Manga"): LibraryManga {
        return LibraryManga(
            id = id,
            sourceId = sourceId,
            url = "/manga/test",
            title = title,
            thumbnailUrl = null,
            chapterCount = 1L,
            unreadCount = 1L,
            author = null,
        )
    }

    private fun createChapter(id: Long = 10L, mangaId: Long = 1L, name: String = "Chapter 1"): LibraryChapter {
        return LibraryChapter(
            id = id,
            mangaId = mangaId,
            url = "/chapter/1",
            name = name,
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
    }

    @Test
    fun `downloads chapter pages, finalizes atomically, and updates DB`(@TempDir tempDir: Path) = runBlocking {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)
        val downloadRoot = tempDir.resolve("downloads")
        val diskProvider = DownloadDiskProvider(downloadRoot)

        val page1Url = "http://127.0.0.1:$serverPort/page1.jpg"
        val page2Url = "http://127.0.0.1:$serverPort/page2.jpg"

        server.createContext("/page1.jpg") { exchange ->
            val bytes = "image-bytes-1".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/page2.jpg") { exchange ->
            val bytes = "image-bytes-2".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

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

        var completedDownload: DesktopDownload? = null

        val downloader = DesktopDownloader(
            store = store,
            diskProvider = diskProvider,
            networkHelper = networkHelper,
            mutationPort = fakeMutationPort,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, page1Url, page1Url),
                    Page(1, page2Url, page2Url),
                )
            },
            onDownloadCompleted = { completedDownload = it },
        )

        val manga = createManga()
        val chapter = createChapter()

        downloader.enqueue(manga, listOf(chapter), autoStart = true)

        withTimeout(5000) {
            while (downloader.queueState.value.firstOrNull()?.status != DownloadStatus.COMPLETED) {
                delay(50)
            }
        }

        val item = downloader.queueState.value.first()
        item.status shouldBe DownloadStatus.COMPLETED
        item.progress shouldBe 1.0f
        item.downloadedImages shouldBe 2
        completedDownload shouldNotBe null

        diskProvider.isChapterDownloaded(manga.sourceId, manga.title, chapter.name) shouldBe true
        val chapterDir = diskProvider.getChapterDir(manga.sourceId, manga.title, chapter.name)
        Files.exists(chapterDir.resolve("001.jpg")) shouldBe true
        Files.exists(chapterDir.resolve("002.jpg")) shouldBe true

        insertedChapters shouldHaveSize 1
        insertedChapters[0].chapterId shouldBe chapter.id
    }

    @Test
    fun `page-level resume skips already downloaded pages`(@TempDir tempDir: Path) = runBlocking {
        val store = DownloadStore(tempDir.resolve("downloads.json"))
        val diskProvider = DownloadDiskProvider(tempDir.resolve("downloads"))

        val manga = createManga()
        val chapter = createChapter()

        // Simulate page 0 already existing in temp dir
        val tempDirChapter = diskProvider.getTempChapterDir(manga.sourceId, manga.title, chapter.name)
        diskProvider.savePage(tempDirChapter, 0, byteArrayOf(99, 98, 97))

        val page2Url = "http://127.0.0.1:$serverPort/page2-resume.jpg"
        val page2RequestCount = AtomicInteger(0)
        server.createContext("/page2-resume.jpg") { exchange ->
            page2RequestCount.incrementAndGet()
            val bytes = "image-bytes-2".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val downloader = DesktopDownloader(
            store = store,
            diskProvider = diskProvider,
            networkHelper = networkHelper,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, "http://skipped.url", "http://skipped.url"),
                    Page(1, page2Url, page2Url),
                )
            },
        )

        downloader.enqueue(manga, listOf(chapter), autoStart = true)

        withTimeout(5000) {
            while (downloader.queueState.value.firstOrNull()?.status != DownloadStatus.COMPLETED) {
                delay(50)
            }
        }

        page2RequestCount.get() shouldBe 1
        diskProvider.isChapterDownloaded(manga.sourceId, manga.title, chapter.name) shouldBe true
    }
}
