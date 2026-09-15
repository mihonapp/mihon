package mihon.desktop.download

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.extension.DesktopNetworkHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

class DownloadRecoveryTest {
    private fun item(id: Long, source: Long = 1) = DesktopDownload(
        chapterId = id,
        mangaId = 1,
        sourceId = source,
        mangaTitle = "Manga",
        chapterName = "Chapter $id",
        chapterUrl = "/$id",
    )

    @Test
    fun `corrupt primary restores last valid snapshot without destroying original`(@TempDir dir: Path) {
        val file = dir.resolve("queue.json")
        val store = DownloadStore(file)
        store.save(listOf(item(1)))
        store.save(listOf(item(1), item(2)))
        Files.writeString(file, "{broken")
        DownloadStore(file).restore().map { it.chapterId } shouldBe listOf(1L)
        Files.readString(file) shouldBe "{broken"
    }

    @Test
    fun `nonempty truncated page cannot finalize a chapter`(@TempDir dir: Path) {
        val disk = DownloadDiskProvider(dir)
        val temp = disk.getTempChapterDir(1, "Manga", "Chapter")
        Files.createDirectories(temp)
        Files.write(disk.getPageFile(temp, 0), byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
        io.kotest.assertions.throwables.shouldThrow<java.io.IOException> {
            disk.finalizeChapter(1, 1, 1, "Manga", "Chapter", 1)
        }
        Files.exists(disk.getChapterDir(1, "Manga", "Chapter")) shouldBe false
    }

    @Test
    fun `parallel workers leave a slot for another source`(@TempDir dir: Path): Unit = runBlocking {
        val store = DownloadStore(dir.resolve("queue.json"))
        store.save(listOf(item(1), item(2), item(3, 2)))
        val started = Collections.synchronizedList(mutableListOf<Long>())
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val downloader = DesktopDownloader(
            store,
            DownloadDiskProvider(dir.resolve("pages")),
            DesktopNetworkHelper(),
            pageListFetcher = { source, _ ->
                started.add(source)
                release.await()
                emptyList()
            },
            downloadParallelism = { 2 },
        )
        try {
            downloader.start()
            withTimeout(5_000) { while (started.size < 2) delay(10) }
            started.toSet() shouldBe setOf(1L, 2L)
            release.complete(Unit)
            withTimeout(5_000) { while (downloader.isRunning.value) delay(10) }
            started.count { it == 1L } shouldBe 2
        } finally {
            release.complete(Unit)
            downloader.close()
        }
    }

    @Test
    fun `single worker alternates queued sources`(@TempDir dir: Path): Unit = runBlocking {
        val store = DownloadStore(dir.resolve("queue.json"))
        store.save(listOf(item(1), item(2), item(3, 2), item(4, 2)))
        val order = Collections.synchronizedList(mutableListOf<Long>())
        val downloader = DesktopDownloader(
            store,
            DownloadDiskProvider(dir.resolve("pages")),
            DesktopNetworkHelper(),
            pageListFetcher = { source, _ ->
                order.add(source)
                emptyList()
            },
        )
        try {
            downloader.start()
            withTimeout(5_000) { while (downloader.isRunning.value) delay(10) }
            order.toList() shouldBe listOf(1L, 2L, 1L, 2L)
        } finally {
            downloader.close()
        }
    }

    @Test
    fun `saving after recovery archives corrupt primary and keeps valid backup`(@TempDir dir: Path) {
        val file = dir.resolve("queue.json")
        val store = DownloadStore(file)
        store.save(listOf(item(1)))
        store.save(listOf(item(2)))
        Files.writeString(file, "broken")
        val recovered = store.restore()
        store.recoveryReport.value!!.recoveredFrom shouldBe dir.resolve("queue.json.bak")
        store.save(recovered)
        Files.readString(store.recoveryReport.value!!.preservedFile!!) shouldBe "broken"
        DownloadStore(file).restore().map { it.chapterId } shouldBe listOf(1L)
    }

    @Test
    fun `failed page write preserves previous page and removes partial file`(@TempDir dir: Path) {
        val disk = DownloadDiskProvider(dir)
        val page = disk.savePage(dir, 0, validDownloadImage())
        val previous = Files.readAllBytes(page).toList()
        io.kotest.assertions.throwables.shouldThrow<java.io.IOException> {
            disk.savePage(dir, 0, "truncated".toByteArray())
        }
        Files.readAllBytes(page).toList() shouldBe previous
        Files.exists(dir.resolve("001.jpg.part")) shouldBe false
    }

    @Test
    fun `unreadable volume is not considered to have free space`(@TempDir dir: Path) {
        val root = dir.resolve("volume")
        val disk = DownloadDiskProvider(root)
        Files.delete(root)
        disk.checkDiskSpace() shouldBe false
    }

    @Test
    fun `restart repairs only damaged page and retains valid page`(@TempDir dir: Path): Unit = runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val bytes = validDownloadImage()
        server.createContext("/image") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val disk = DownloadDiskProvider(dir.resolve("pages"))
        val temp = disk.getTempChapterDir(1, "Manga", "Chapter 1")
        disk.savePage(temp, 0, bytes)
        Files.write(disk.getPageFile(temp, 1), "damaged".toByteArray())
        val url = "http://127.0.0.1:${server.address.port}/image"
        val store = DownloadStore(dir.resolve("queue.json"))
        store.save(
            listOf(
                item(1).copy(
                    status = DownloadStatus.DOWNLOADING,
                    pages = listOf(
                        DownloadPage(0, url, imageUrl = url, status = PageStatus.READY),
                        DownloadPage(1, url, imageUrl = url, status = PageStatus.READY),
                    ),
                ),
            ),
        )
        val downloader = DesktopDownloader(store, disk, DesktopNetworkHelper())
        try {
            downloader.start()
            withTimeout(5_000) { while (downloader.isRunning.value) delay(10) }
            downloader.queueState.value.single().status shouldBe DownloadStatus.COMPLETED
            requests.get() shouldBe 1
            downloader.queueState.value.single().bytesDownloaded shouldBe bytes.size * 2L
        } finally {
            downloader.close()
            server.stop(0)
        }
    }
}
