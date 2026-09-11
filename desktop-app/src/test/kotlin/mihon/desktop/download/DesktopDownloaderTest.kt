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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DesktopDownloaderTest {

    @Test
    fun `failed page does not prevent later pages from downloading`(@TempDir tempDir: Path): Unit = runBlocking {
        val goodRequests = AtomicInteger(0)
        server.createContext("/blocked") {
            it.sendResponseHeaders(403, -1)
            it.close()
        }
        server.createContext("/good") { exchange ->
            goodRequests.incrementAndGet()
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("queue.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("downloads")),
            networkHelper = networkHelper,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(0, imageUrl = "http://127.0.0.1:$serverPort/blocked"),
                    Page(1, imageUrl = "http://127.0.0.1:$serverPort/good"),
                )
            },
        )
        try {
            downloader.enqueue(createManga(), listOf(createChapter()))
            withTimeout(5_000) { while (downloader.queueState.value.single().status != DownloadStatus.ERROR) delay(20) }
            goodRequests.get() shouldBe 1
            downloader.queueState.value.single().pages[1].status shouldBe PageStatus.READY
        } finally {
            downloader.close()
        }
    }

    private lateinit var server: HttpServer
    private lateinit var networkHelper: DesktopNetworkHelper
    private lateinit var serverExecutor: ExecutorService
    private var serverPort: Int = 0

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverExecutor = Executors.newCachedThreadPool()
        server.executor = serverExecutor
        server.start()
        serverPort = server.address.port
        networkHelper = DesktopNetworkHelper()
        networkHelper.registerExtensionDomains("test.ext", listOf("127.0.0.1", "localhost"))
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        serverExecutor.shutdownNow()
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
    fun `downloads chapter pages, finalizes atomically, and updates DB`(@TempDir tempDir: Path): Unit = runBlocking {
        val storeFile = tempDir.resolve("downloads.json")
        val store = DownloadStore(storeFile)
        val downloadRoot = tempDir.resolve("downloads")
        val diskProvider = DownloadDiskProvider(downloadRoot)

        val page1Url = "http://127.0.0.1:$serverPort/page1.jpg"
        val page2Url = "http://127.0.0.1:$serverPort/page2.jpg"

        server.createContext("/page1.jpg") { exchange ->
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/page2.jpg") { exchange ->
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
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
    fun `preserves extension page headers when downloading images`(@TempDir tempDir: Path): Unit = runBlocking {
        networkHelper.unregisterExtensionDomains("test.ext")
        val pageUrl = "http://127.0.0.1:$serverPort/protected.jpg"
        server.createContext("/protected.jpg") { exchange ->
            val authorized = exchange.requestHeaders.getFirst("X-Image-Token") == "page-token" &&
                exchange.requestHeaders.getFirst("Referer") == "https://source.example/gallery/1"
            val bytes = if (authorized) {
                byteArrayOf(
                    0xff.toByte(),
                    0xd8.toByte(),
                    0xff.toByte(),
                    0xe0.toByte(),
                )
            } else {
                "forbidden".toByteArray()
            }
            exchange.sendResponseHeaders(if (authorized) 200 else 403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("protected.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("downloads")),
            networkHelper = networkHelper,
            pageListFetcher = { _, _ ->
                listOf(
                    Page(
                        index = 0,
                        imageUrl = pageUrl,
                        headers = mapOf(
                            "X-Image-Token" to "page-token",
                            "referer" to "https://source.example/gallery/1",
                        ),
                    ),
                )
            },
        )

        downloader.enqueue(createManga(), listOf(createChapter()), autoStart = true)

        withTimeout(5_000) {
            while (downloader.queueState.value.single().status !in
                setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR)
            ) {
                delay(25)
            }
        }
        downloader.queueState.value.single().status shouldBe DownloadStatus.COMPLETED
        downloader.close()
    }

    @Test
    fun `page-level resume skips already downloaded pages`(@TempDir tempDir: Path): Unit = runBlocking {
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
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
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

    @Test
    fun `bounds concurrent chapter and page downloads without duplicate requests`(@TempDir tempDir: Path): Unit =
        runBlocking {
            val active = AtomicInteger(0)
            val maximum = AtomicInteger(0)
            val requests = Array(6) { AtomicInteger(0) }
            repeat(6) { index ->
                server.createContext("/parallel-$index.jpg") { exchange ->
                    requests[index].incrementAndGet()
                    val now = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, now) }
                    try {
                        Thread.sleep(120)
                        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }

            val downloader = DesktopDownloader(
                store = DownloadStore(tempDir.resolve("parallel.json")),
                diskProvider = DownloadDiskProvider(tempDir.resolve("parallel-downloads")),
                networkHelper = networkHelper,
                downloadParallelism = { 2 },
                pageParallelism = { 2 },
                pageListFetcher = { _, chapterUrl ->
                    val chapterIndex = chapterUrl.substringAfterLast('/').toInt()
                    (0 until 2).map { pageIndex ->
                        val requestIndex = chapterIndex * 2 + pageIndex
                        val url = "http://127.0.0.1:$serverPort/parallel-$requestIndex.jpg"
                        Page(pageIndex, url, url)
                    }
                },
            )

            val manga = createManga(title = "Parallel Manga")
            val chapters = (0 until 3).map { index ->
                createChapter(id = 100L + index, name = "Chapter ${index + 1}").copy(url = "/$index")
            }
            downloader.enqueue(manga, chapters)

            withTimeout(10_000) {
                while (downloader.queueState.value.count { it.status == DownloadStatus.COMPLETED } != 3) {
                    delay(25)
                }
            }

            (maximum.get() > 1) shouldBe true
            (maximum.get() <= 4) shouldBe true
            requests.forEach { it.get() shouldBe 1 }
            downloader.close()
        }

    @Test
    fun `pause resume cancel and retry preserve queue semantics`(@TempDir tempDir: Path): Unit = runBlocking {
        val pageUrl = "http://127.0.0.1:$serverPort/control.jpg"
        val requests = AtomicInteger(0)
        server.createContext("/control.jpg") { exchange ->
            requests.incrementAndGet()
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val pauseFetchAttempts = AtomicInteger(0)
        val retryFetchAttempts = AtomicInteger(0)
        val downloader = DesktopDownloader(
            store = DownloadStore(tempDir.resolve("control.json")),
            diskProvider = DownloadDiskProvider(tempDir.resolve("control-downloads")),
            networkHelper = networkHelper,
            pageListFetcher = { _, chapterUrl ->
                when (chapterUrl) {
                    "/pause" -> if (pauseFetchAttempts.incrementAndGet() == 1) delay(500)
                    "/cancel" -> delay(500)
                    "/retry" -> if (retryFetchAttempts.incrementAndGet() == 1) {
                        error("temporary page-list failure")
                    }
                }
                listOf(Page(0, pageUrl, pageUrl))
            },
        )
        val manga = createManga(title = "Control Manga")
        val first = createChapter(id = 201L, name = "Pause Me").copy(url = "/pause")
        downloader.enqueue(manga, listOf(first))
        withTimeout(2_000) {
            while (downloader.queueState.value.single().status != DownloadStatus.DOWNLOADING) delay(10)
        }
        downloader.pause()
        downloader.queueState.value.single().status shouldBe DownloadStatus.PAUSED
        downloader.resume()
        withTimeout(5_000) {
            while (downloader.queueState.value.single().status != DownloadStatus.COMPLETED) delay(20)
        }
        requests.get() shouldBe 1

        val cancelled = createChapter(id = 202L, name = "Cancel Me").copy(url = "/cancel")
        downloader.enqueue(manga, listOf(cancelled))
        withTimeout(2_000) {
            while (downloader.queueState.value.none {
                    it.chapterId == cancelled.id &&
                        it.status == DownloadStatus.DOWNLOADING
                }
            ) {
                delay(10)
            }
        }
        downloader.cancel(cancelled.id)
        downloader.queueState.value.none { it.chapterId == cancelled.id } shouldBe true

        val retry = createChapter(id = 203L, name = "Retry Me").copy(url = "/retry")
        downloader.enqueue(manga, listOf(retry))
        withTimeout(2_000) {
            while (downloader.queueState.value.none { it.chapterId == retry.id && it.status == DownloadStatus.ERROR }) {
                delay(10)
            }
        }
        downloader.retry(retry.id)
        withTimeout(5_000) {
            while (downloader.queueState.value.none {
                    it.chapterId == retry.id && it.status == DownloadStatus.COMPLETED
                }
            ) {
                delay(20)
            }
        }
        downloader.close()
    }
}
