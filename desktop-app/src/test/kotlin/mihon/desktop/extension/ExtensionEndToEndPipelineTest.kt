package mihon.desktop.extension

import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import mihon.reader.model.PageId
import mihon.reader.session.ReaderProgressUpdate
import mihon.reader.source.ReaderChapterAsset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionEndToEndPipelineTest {

    private lateinit var mockServer: HttpServer
    private var mockServerPort: Int = 0
    private val json = Json { prettyPrint = true }

    @BeforeEach
    fun setUp() {
        mockServer = HttpServer.create(InetSocketAddress(0), 0)

        // Mock repo index
        mockServer.createContext("/repo/index.min.json") { exchange ->
            val storeItem = """
            [
                {
                    "name": "E2E Manga Source",
                    "pkg": "ext.e2e.sample",
                    "apk": "ext-e2e-sample.mext",
                    "lang": "en",
                    "code": 1,
                    "version": "1.0.0",
                    "nsfw": 0,
                    "sources": [
                        {
                            "name": "E2E Source",
                            "id": 9999,
                            "baseUrl": "http://127.0.0.1:$mockServerPort",
                            "lang": "en"
                        }
                    ]
                }
            ]
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, storeItem.size.toLong())
            exchange.responseBody.use { it.write(storeItem) }
        }

        // Mock page image
        mockServer.createContext("/page1.jpg") { exchange ->
            val img = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) // JPEG
            exchange.sendResponseHeaders(200, img.size.toLong())
            exchange.responseBody.use { it.write(img) }
        }

        mockServer.start()
        mockServerPort = mockServer.address.port
    }

    @AfterEach
    fun tearDown() {
        mockServer.stop(0)
    }

    private fun createSampleMext(file: File, manifest: ExtensionManifest) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            val manifestEntry = ZipEntry("manifest.json")
            zip.putNextEntry(manifestEntry)
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()

            val iconEntry = ZipEntry("icon.png")
            zip.putNextEntry(iconEntry)
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zip.closeEntry()
        }
    }

    @Test
    fun `complete extension pipeline - catalog, install, permissions, sync to db, and online reading`(
        @TempDir tempDir: Path,
    ) {
        runBlocking {
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installRoot = tempDir.resolve("extensions").toFile()
            val installer = DesktopExtensionInstaller(installRoot, prefStore)
            val networkHelper = DesktopNetworkHelper()
            val storeService = ExtensionStoreService(prefStore)

            // Step 1: Repository configuration & catalog
            val repoUrl = "http://127.0.0.1:$mockServerPort/repo"
            storeService.addRepository(repoUrl)
            storeService.getRepositories().contains(repoUrl) shouldBe true

            // Step 2: Install local sample extension with declared domains
            val manifest = ExtensionManifest(
                id = "ext.e2e.sample",
                name = "E2E Manga Source",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(
                        id = 9999L,
                        name = "E2E Source",
                        lang = "en",
                        className = "ext.e2e.E2eSource",
                    ),
                ),
                declaredDomains = listOf("127.0.0.1"),
            )

            val mextFile = tempDir.resolve("ext-e2e-sample.mext").toFile()
            createSampleMext(mextFile, manifest)

            val installed = installer.installFromLocalFile(mextFile)
            installed.pkg shouldBe "ext.e2e.sample"
            installed.manifest.declaredDomains shouldBe listOf("127.0.0.1")

            // Register permissions with network helper
            networkHelper.registerExtensionDomains(installed.pkg, installed.manifest.declaredDomains)
            networkHelper.isDomainAllowed("127.0.0.1") shouldBe true
            networkHelper.isDomainAllowed("malicious.site.com") shouldBe false

            // Step 3: Process manager and database integration
            val dbFile = tempDir.resolve("library.db")
            val libraryRepo = DesktopLibraryDatabaseFactory.open(dbFile)
            val processManager = WindowsExtensionProcessManager(tempDir.resolve("worker").toFile())

            val syncService = OnlineMangaSyncService(libraryRepo, processManager)

            // Add manga to library
            val onlineManga = SManga(
                url = "/manga/e2e-one",
                title = "E2E Adventure",
                author = "Author E2E",
                genre = listOf("Action"),
            )

            val mangaId = syncService.addOrUpdateOnlineManga(9999L, onlineManga)
            mangaId shouldBe 1L
            syncService.isMangaInLibrary(9999L, "/manga/e2e-one") shouldBe true

            val savedManga = libraryRepo.mangaSnapshot(mangaId)
            savedManga?.title shouldBe "E2E Adventure"
            savedManga?.favorite shouldBe true

            // Insert chapter for the manga
            val chapterId = libraryRepo.insertChapter(
                mihon.desktop.library.model.ChapterRecord(
                    id = 0L,
                    mangaId = mangaId,
                    url = "/chapter/1",
                    name = "Chapter 1",
                    scanlator = "Scanlator",
                    read = false,
                    bookmark = false,
                    lastPageRead = 0L,
                    dateFetch = System.currentTimeMillis(),
                    dateUpload = System.currentTimeMillis(),
                    chapterNumber = 1.0,
                    sourceOrder = 0L,
                    lastModifiedAt = System.currentTimeMillis(),
                ),
            )

            // Step 4: Online chapter reading and progress persistence
            val asset = ReaderChapterAsset(
                mangaId = mangaId,
                chapterId = chapterId,
                mangaTitle = "E2E Adventure",
                chapterName = "Chapter 1",
                storageRoot = tempDir,
                relativePath = Path.of("none"),
                assetKind = "ONLINE",
                sizeBytes = 0L,
                modifiedAt = 0L,
                lastPageRead = 0L,
                read = false,
            )

            val onlineChapterSource = OnlineChapterSource(
                asset = asset,
                sourceId = 9999L,
                chapter = SChapter(url = "/chapter/1", name = "Chapter 1"),
                processManager = processManager,
                networkHelper = networkHelper,
                cacheDir = tempDir.resolve("reader-cache").toFile(),
            )
            onlineChapterSource.cachedPages =
                listOf(Page(index = 0, imageUrl = "http://127.0.0.1:$mockServerPort/page1.jpg"))

            val pages = onlineChapterSource.pages()
            pages shouldHaveSize 1

            val pageInput = onlineChapterSource.open(PageId(chapterId.toString(), "page_0000.jpg"))
            val bytes = pageInput.input.readBytes()
            bytes.size shouldBe 4
            bytes[0] shouldBe 0xFF.toByte()

            // Update reading progress in database
            val writeResult = libraryRepo.record(
                ReaderProgressUpdate(
                    chapterId = chapterId,
                    pageIndex = 0L,
                    completed = true,
                    lastReadEpochMillis = System.currentTimeMillis(),
                    readDurationDeltaMillis = 5000L,
                    generation = 1L,
                    sequence = 1L,
                ),
            )
            writeResult.name shouldBe "APPLIED"

            // Step 5: Clean shutdown
            libraryRepo.close()
            processManager.close()
        }
    }
}
