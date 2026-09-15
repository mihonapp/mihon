package mihon.desktop.extension

import com.sun.jna.Platform
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.download.DesktopDownloader
import mihon.desktop.download.DownloadDiskProvider
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.DownloadStore
import mihon.desktop.library.backup.AndroidBackupCodec
import mihon.desktop.library.backup.AndroidBackupExporter
import mihon.desktop.library.backup.AndroidBackupImporter
import mihon.desktop.library.backup.AndroidBackupValidator
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.SourceRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderFactory
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.updates.DesktopLibraryUpdateService
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.reader.session.ReaderAction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SuwayomiWorkflowIntegrationTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `real isolated extension to SQLite download offline reader update and backup`() = runBlocking {
        assumeTrue(Platform.isWindows())
        val requests = CopyOnWriteArrayList<String>()
        val count = AtomicInteger(1)
        val image = ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(
                java.awt.image.BufferedImage(32, 48, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png",
                it,
            )
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base = "http://127.0.0.1:${server.address.port}"
        val imageBase = "http://localhost:${server.address.port}"
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += path
            val bytes = when {
                path == "/search" -> "Fixture Manga".toByteArray()
                path == "/manga" -> "Fixture Author".toByteArray()
                path == "/manga/chapters" -> count.get().toString().toByteArray()
                path.endsWith("/pages") -> "$imageBase/one.png\n$imageBase/two.png".toByteArray()
                path.endsWith(".png") -> image
                else -> error("Unexpected fixture request: $path")
            }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val prefs = DesktopPreferenceStore(temp.resolve("preferences.properties"))
        val installer = DesktopExtensionInstaller(temp.resolve("extensions").toFile(), prefs)
        val installed = installer.installFromLocalFile(createPackage(), trustOnInstall = true)
        val network = DesktopNetworkHelper()
        network.registerExtensionDomains(installed.pkg, installed.manifest.declaredDomains)
        val manager = WindowsExtensionProcessManager(
            temp.resolve("hosts").toFile(),
            onBrokerHttp = network::executeBrokeredRequest,
            networkHelper = network,
        )
        val sources = DesktopSourceManager(installer = installer, processManager = manager)
        val repository = DesktopLibraryDatabaseFactory.open(temp.resolve("library.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val downloader = DesktopDownloader(
            DownloadStore(temp.resolve("queue.json")),
            DownloadDiskProvider(temp.resolve("downloads")),
            network,
            sourceManager = sources,
            mutationPort = repository,
        )
        try {
            val found = sources.searchManga(99001L, 1, base).mangas.single()
            assertEquals("Fixture Manga", found.title)
            assertEquals("Fixture Author", sources.getMangaDetails(99001L, found).author)
            assertTrue(repository.librarySnapshot().isEmpty())
            val sync = OnlineMangaSyncService(repository, sources)
            val mangaId = sync.prepareOnlineMangaForReading(99001L, found)
            assertFalse(sync.isMangaInLibrary(99001L, found.url))
            assertEquals(mangaId, sync.addOrUpdateOnlineManga(99001L, found))
            repository.upsertSource(SourceRecord(99001L, "Workflow fixture", System.currentTimeMillis()))
            val chapter = repository.chapterSnapshot(mangaId).single()
            val sourcePage = sources.getPageList(
                99001L,
                mihon.extension.source.model.SChapter(url = chapter.url, name = chapter.name),
            ).first()
            assertFalse(network.isDomainAllowed("localhost", installed.pkg))
            assertArrayEquals(image, sources.getImage(99001L, sourcePage))
            assertTrue(network.isDomainAllowed("localhost", installed.pkg))
            assertFalse(network.isDomainAllowed("localhost", "unrelated.extension"))
            assertFalse(network.isDomainAllowed("unrelated.invalid", installed.pkg))
            downloader.enqueue(repository.librarySnapshot().single(), listOf(chapter))
            withTimeout(30_000) {
                while (downloader.queueState.value.single().status !in
                    setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR)
                ) {
                    delay(50)
                }
            }
            assertEquals(
                DownloadStatus.COMPLETED,
                downloader.queueState.value.single().status,
                downloader.queueState.value.single().toString(),
            )
            assertTrue(
                requests.containsAll(
                    listOf("/search", "/manga", "/manga/chapters", "/manga/1/pages", "/one.png", "/two.png"),
                ),
            )
            sources.close()
            manager.close()
            val requestCount = requests.size
            val factory = DesktopReaderFactory(scope, repository, DesktopReaderSettingsStore(prefs))
            val session = factory.createSession()
            session.open(chapter.id)
            withTimeout(10_000) { while (session.state.value.pages.size != 2) delay(25) }
            val frame = factory.loadFrame(session.state.value.pages.first().id, 0)
            assertEquals(32, frame.metadata.width)
            assertEquals(48, frame.metadata.height)
            for (mode in mihon.reader.model.ReadingMode.entries) {
                session.dispatch(ReaderAction.ChangeMode(mode))
                withTimeout(5_000) { while (session.state.value.mode != mode) delay(10) }
            }
            session.dispatch(ReaderAction.SelectPage(1))
            withTimeout(5_000) { while (session.state.value.selectedIndex != 1) delay(10) }
            session.flushProgress()
            session.closeAndFlush()
            factory.closeServices()
            assertEquals(requestCount, requests.size, "Offline reader must not issue HTTP requests")
            assertEquals(1L, repository.chapterSnapshot(mangaId).single().lastPageRead)
            assertTrue(repository.allHistorySnapshot().isNotEmpty())
            // A new real host reads the changed HTTP chapter list; the old host stays closed.
            count.set(2)
            WindowsExtensionProcessManager(
                temp.resolve("update-host").toFile(),
                onBrokerHttp = network::executeBrokeredRequest,
                networkHelper = network,
            ).use { updater ->
                updater.loadExtension(java.io.File(installed.packageFile))
                val result = DesktopLibraryUpdateService(
                    repository,
                    repository,
                    processManager = updater,
                ).updateLibrary()
                assertEquals(1, result.newChaptersFound)
            }
            val backup = temp.resolve("roundtrip.tachibk")
            AndroidBackupExporter(repository).export(backup)
            DesktopLibraryDatabaseFactory.open(temp.resolve("restored.db")).use { restored ->
                val importer = AndroidBackupImporter(AndroidBackupCodec(), AndroidBackupValidator(), restored)
                importer.import(backup, System.currentTimeMillis())
                importer.import(backup, System.currentTimeMillis() + 1)
                val restoredManga = restored.librarySnapshot().single()
                assertEquals(99001L, restoredManga.sourceId)
                assertEquals(2, restored.chapterSnapshot(restoredManga.id).size)
                assertEquals(
                    1L,
                    restored.chapterSnapshot(restoredManga.id).single {
                        it.url == chapter.url
                    }.lastPageRead,
                )
                assertEquals(1, restored.allHistorySnapshot().size)
            }
        } finally {
            downloader.close()
            sources.close()
            manager.close()
            scope.cancel()
            repository.close()
            network.close()
            server.stop(0)
        }
    }

    @Test
    fun `restored failed downloads grant their image host before the first extension request`() = runBlocking {
        assumeTrue(Platform.isWindows())
        val image = ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(
                java.awt.image.BufferedImage(12, 16, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png",
                it,
            )
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<String>()
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path
            exchange.sendResponseHeaders(200, image.size.toLong())
            exchange.responseBody.use { it.write(image) }
        }
        server.start()
        val prefs = DesktopPreferenceStore(temp.resolve("preferences.properties"))
        val installer = DesktopExtensionInstaller(temp.resolve("extensions").toFile(), prefs)
        val installed = installer.installFromLocalFile(createPackage(), trustOnInstall = true)
        val network = DesktopNetworkHelper()
        val manager = WindowsExtensionProcessManager(
            temp.resolve("hosts").toFile(),
            onBrokerHttp = network::executeBrokeredRequest,
            networkHelper = network,
        )
        val sources = DesktopSourceManager(installer = installer, processManager = manager)
        val store = DownloadStore(temp.resolve("queue.json"))
        val imageUrl = "http://localhost:${server.address.port}/restored.png"
        store.save(
            listOf(
                mihon.desktop.download.DesktopDownload(
                    chapterId = 9L,
                    mangaId = 8L,
                    sourceId = 99001L,
                    mangaTitle = "Fixture",
                    chapterName = "Saved chapter",
                    chapterUrl = "http://127.0.0.1:${server.address.port}/chapter",
                    status = DownloadStatus.ERROR,
                    pages = listOf(
                        mihon.desktop.download.DownloadPage(
                            index = 0,
                            url = imageUrl,
                            imageUrl = imageUrl,
                            status = mihon.desktop.download.PageStatus.ERROR,
                            error = "Domain denied",
                        ),
                    ),
                    error = "Domain denied",
                ),
            ),
        )
        val downloader = DesktopDownloader(
            store,
            DownloadDiskProvider(temp.resolve("downloads")),
            network,
            sourceManager = sources,
        )
        try {
            assertFalse(network.isDomainAllowed("localhost", installed.pkg))
            downloader.retry(9L)
            withTimeout(30_000) {
                while (downloader.queueState.value.single().status !in
                    setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR)
                ) {
                    delay(50)
                }
            }
            val result = downloader.queueState.value.single()
            assertEquals(DownloadStatus.COMPLETED, result.status, result.error)
            assertEquals(1, result.downloadedImages)
            assertEquals(image.size.toLong(), result.bytesDownloaded)
            assertEquals(listOf("/restored.png"), requests)
            assertFalse(network.isDomainAllowed("localhost", "unrelated.extension"))
            assertFalse(network.isDomainAllowed("unrelated.invalid", installed.pkg))
        } finally {
            downloader.close()
            sources.close()
            network.close()
            server.stop(0)
        }
    }

    private fun createPackage(): java.io.File {
        val type = WorkflowHttpSource::class.java
        val manifest = ExtensionManifest(
            "test.workflow",
            "Local Workflow",
            "1.0",
            1,
            1.0,
            "en",
            sources = listOf(SourceDescriptor(99001L, "Workflow fixture", "en", type.name)),
            declaredDomains = listOf("127.0.0.1"),
        )
        val jar = ByteArrayOutputStream()
        JarOutputStream(jar).use { output ->
            val path = type.name.replace('.', '/') + ".class"
            output.putNextEntry(JarEntry(path))
            type.classLoader.getResourceAsStream(path)!!.use { it.copyTo(output) }
            output.closeEntry()
        }
        return temp.resolve("fixture.mext").toFile().also { file ->
            ZipOutputStream(file.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("manifest.json"))
                output.write(
                    kotlinx.serialization.json.Json.encodeToString(
                        ExtensionManifest.serializer(),
                        manifest,
                    ).toByteArray(),
                )
                output.closeEntry()
                output.putNextEntry(ZipEntry("classes.jar"))
                output.write(jar.toByteArray())
                output.closeEntry()
            }
        }
    }
}
