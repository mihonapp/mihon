package mihon.extension.host

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.IpcCallbackResponse
import mihon.extension.ipc.IpcCallbacks
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.ipc.IpcResponse
import mihon.extension.ipc.IpcSession
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.decodeFilterList
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path

class ExtensionHostEngineTest {

    private val json = Json { ignoreUnknownKeys = true }

    class CountingSourceFactory : SourceFactory {
        override fun createSources(): List<Source> {
            invocations++
            return listOf(
                CountingCatalogueSource(1001L, "Test Source"),
                CountingCatalogueSource(2002L, "Test Source 2"),
            )
        }

        companion object {
            var invocations: Int = 0
        }
    }

    class CountingCatalogueSource(
        override val id: Long,
        override val name: String,
    ) : CatalogueSource {
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int) =
            eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int) =
            eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: eu.kanade.tachiyomi.source.model.FilterList,
        ) = eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)

        override suspend fun getMangaUpdate(
            manga: eu.kanade.tachiyomi.source.model.SManga,
            chapters: List<eu.kanade.tachiyomi.source.model.SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = eu.kanade.tachiyomi.source.model.SMangaUpdate(manga, chapters)

        override suspend fun getPageList(
            chapter: eu.kanade.tachiyomi.source.model.SChapter,
        ): List<eu.kanade.tachiyomi.source.model.Page> = emptyList()
    }

    class TestCatalogueSource : WindowsCatalogueSource {
        override val id: Long = 1001L
        override val name: String = "Test Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int): MangasPage {
            return MangasPage(
                mangas = listOf(SManga(url = "/manga/1", title = "Test Manga $page")),
                hasNextPage = false,
            )
        }

        override suspend fun getLatestUpdates(page: Int): MangasPage {
            return MangasPage(
                mangas = listOf(SManga(url = "/manga/latest", title = "Latest Manga $page")),
                hasNextPage = true,
            )
        }

        override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
            if (query == "error") throw IllegalStateException("Search forced failure")
            return MangasPage(
                mangas = listOf(SManga(url = "/manga/search", title = "Query: $query")),
                hasNextPage = false,
            )
        }

        override suspend fun getMangaDetails(manga: SManga): SManga {
            return manga.copy(author = "Test Author", description = "Test Description")
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            return listOf(SChapter(url = "/chapter/1", name = "Chapter 1", chapterNumber = 1.0f))
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            return listOf(
                Page(index = 0, url = "https://example.com/p0.jpg", imageUrl = "https://example.com/p0.jpg"),
                Page(index = 1, url = "https://example.com/p1.jpg", imageUrl = "https://example.com/p1.jpg"),
            )
        }

        override fun getFilterList(): FilterList {
            return FilterList(
                Filter.Text("Author", ""),
                Filter.CheckBox("Completed", false),
            )
        }
    }

    @Test
    fun `engine handles ping command`() {
        runBlocking {
            val client = BrokeredHttpClient { null }
            val engine = ExtensionHostEngine(client)

            val res = engine.handleRequest(IpcRequest(1, IpcCommands.PING))
            res.success shouldBe true
            res.payloadJson shouldBe "pong"
        }
    }

    @Test
    fun `engine executes registered source operations`() {
        runBlocking {
            val client = BrokeredHttpClient { null }
            val engine = ExtensionHostEngine(client)
            val source = TestCatalogueSource()
            engine.registerSource(source)

            // GET_SOURCES
            val sourcesRes = engine.handleRequest(IpcRequest(1, IpcCommands.GET_SOURCES))
            sourcesRes.success shouldBe true
            val descriptors = json.decodeFromString<List<SourceDescriptor>>(sourcesRes.payloadJson)
            descriptors.shouldHaveSize(1)
            descriptors[0].id shouldBe 1001L
            descriptors[0].name shouldBe "Test Source"

            // GET_POPULAR
            val popularRes = engine.handleRequest(
                IpcRequest(2, IpcCommands.GET_POPULAR, json.encodeToString(GetPagePayload(1001L, 1))),
            )
            popularRes.success shouldBe true
            val popularPage = json.decodeFromString<MangasPage>(popularRes.payloadJson)
            popularPage.mangas.shouldHaveSize(1)
            popularPage.mangas[0].title shouldBe "Test Manga 1"

            // GET_LATEST
            val latestRes = engine.handleRequest(
                IpcRequest(3, IpcCommands.GET_LATEST, json.encodeToString(GetPagePayload(1001L, 2))),
            )
            latestRes.success shouldBe true
            val latestPage = json.decodeFromString<MangasPage>(latestRes.payloadJson)
            latestPage.hasNextPage shouldBe true

            // SEARCH_MANGA
            val searchRes = engine.handleRequest(
                IpcRequest(4, IpcCommands.SEARCH_MANGA, json.encodeToString(SearchPayload(1001L, 1, "Solo"))),
            )
            searchRes.success shouldBe true
            val searchPage = json.decodeFromString<MangasPage>(searchRes.payloadJson)
            searchPage.mangas[0].title shouldBe "Query: Solo"

            // GET_MANGA_DETAILS
            val detailsRes = engine.handleRequest(
                IpcRequest(
                    5,
                    IpcCommands.GET_MANGA_DETAILS,
                    json.encodeToString(MangaPayload(1001L, json.encodeToString(SManga("/manga/1", "Test")))),
                ),
            )
            detailsRes.success shouldBe true
            val details = json.decodeFromString<SManga>(detailsRes.payloadJson)
            details.author shouldBe "Test Author"

            // GET_CHAPTER_LIST
            val chaptersRes = engine.handleRequest(
                IpcRequest(
                    6,
                    IpcCommands.GET_CHAPTER_LIST,
                    json.encodeToString(MangaPayload(1001L, json.encodeToString(SManga("/manga/1", "Test")))),
                ),
            )
            chaptersRes.success shouldBe true
            val chapters = json.decodeFromString<List<SChapter>>(chaptersRes.payloadJson)
            chapters.shouldHaveSize(1)
            chapters[0].name shouldBe "Chapter 1"

            // GET_PAGE_LIST
            val pagesRes = engine.handleRequest(
                IpcRequest(
                    7,
                    IpcCommands.GET_PAGE_LIST,
                    json.encodeToString(ChapterPayload(1001L, json.encodeToString(SChapter("/c1", "C1")))),
                ),
            )
            pagesRes.success shouldBe true
            val pages = json.decodeFromString<List<Page>>(pagesRes.payloadJson)
            pages.shouldHaveSize(2)

            // GET_FILTER_LIST
            val filtersRes = engine.handleRequest(
                IpcRequest(8, IpcCommands.GET_FILTER_LIST, json.encodeToString(SourcePayload(1001L))),
            )
            filtersRes.success shouldBe true
            val filterList = decodeFilterList(filtersRes.payloadJson)
            filterList.filters.map { it.name } shouldBe listOf("Author", "Completed")
        }
    }

    @Test
    fun `engine traps source exceptions safely`() {
        runBlocking {
            val client = BrokeredHttpClient { null }
            val engine = ExtensionHostEngine(client)
            engine.registerSource(TestCatalogueSource())

            val res = engine.handleRequest(
                IpcRequest(1, IpcCommands.SEARCH_MANGA, json.encodeToString(SearchPayload(1001L, 1, "error"))),
            )
            res.success shouldBe false
            res.error shouldContain "Search forced failure"

            val unknownSourceRes = engine.handleRequest(
                IpcRequest(2, IpcCommands.GET_POPULAR, json.encodeToString(GetPagePayload(9999L, 1))),
            )
            unknownSourceRes.success shouldBe false
            unknownSourceRes.error shouldContain "Source with ID 9999 not found"
        }
    }

    @Test
    fun `brokered http client executes callbacks across duplex session`() {
        runBlocking {
            val appOut = PipedOutputStream()
            val hostIn = PipedInputStream(appOut)
            val hostOut = PipedOutputStream()
            val appIn = PipedInputStream(hostOut)

            var hostSessionRef: IpcSession? = null
            val httpClient = BrokeredHttpClient { hostSessionRef }

            // Host session with request handler calling httpClient
            val hostSession = IpcSession(
                input = hostIn,
                output = hostOut,
                onRequest = { request ->
                    if (request.command == "fetch_url") {
                        val body = httpClient.get("https://example.com/api/test")
                        IpcResponse(request.requestId, success = true, payloadJson = body)
                    } else {
                        IpcResponse(request.requestId, success = false, error = "unknown")
                    }
                },
            )
            hostSessionRef = hostSession

            // Main app session responding to BROKER_HTTP callbacks
            val appSession = IpcSession(
                input = appIn,
                output = appOut,
                onCallback = { callback ->
                    if (callback.callbackType == IpcCallbacks.BROKER_HTTP) {
                        val httpReq = json.decodeFromString<BrokerHttpRequest>(callback.payloadJson)
                        httpReq.url shouldBe "https://example.com/api/test"
                        val httpRes = BrokerHttpResponse(
                            statusCode = 200,
                            body = "{\"status\":\"ok\"}",
                            headers = mapOf("content-type" to "application/json"),
                        )
                        IpcCallbackResponse(
                            callback.requestId,
                            success = true,
                            payloadJson = json.encodeToString(httpRes),
                        )
                    } else {
                        IpcCallbackResponse(callback.requestId, success = false, error = "unknown callback")
                    }
                },
            )

            try {
                val response = appSession.sendRequest("fetch_url")
                response shouldBe "{\"status\":\"ok\"}"
            } finally {
                hostSession.close()
                appSession.close()
            }
        }
    }

    class TestCatalogueSource2 : WindowsCatalogueSource {
        override val id: Long = 2002L
        override val name: String = "Test Source 2"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
            mangas = listOf(SManga(url = "/manga/2", title = "Manga 2")),
            hasNextPage = false,
        )
        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun searchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
        override fun getFilterList(): FilterList = FilterList()
    }

    @Test
    fun `engine loads multiple extensions concurrently without wiping sources`(@TempDir tempDir: Path) {
        runBlocking {
            val client = BrokeredHttpClient { null }
            val engine = ExtensionHostEngine(client)

            fun createMext(file: java.io.File, pkg: String, sourceClass: String, sourceId: Long) {
                val manifest = mihon.extension.model.ExtensionManifest(
                    id = pkg,
                    name = pkg,
                    version = "1.0.0",
                    versionCode = 1,
                    libVersion = 1.4,
                    lang = "en",
                    sources = listOf(
                        SourceDescriptor(
                            id = sourceId,
                            name = "Source for $pkg",
                            lang = "en",
                            className = sourceClass,
                        ),
                    ),
                )
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(file)).use { zos ->
                    zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                    zos.write(json.encodeToString(manifest).toByteArray())
                    zos.closeEntry()
                }
            }

            val mext1 = tempDir.resolve("ext1.mext").toFile()
            val mext2 = tempDir.resolve("ext2.mext").toFile()
            createMext(mext1, "ext.one", TestCatalogueSource::class.java.name, 1001L)
            createMext(mext2, "ext.two", TestCatalogueSource2::class.java.name, 2002L)

            val work1 = tempDir.resolve("work1").toFile()
            val work2 = tempDir.resolve("work2").toFile()

            engine.loadExtension(mext1, work1)
            engine.loadExtension(mext2, work2)

            val sourcesRes = engine.handleRequest(IpcRequest(1, IpcCommands.GET_SOURCES))
            sourcesRes.success shouldBe true
            val descriptors = json.decodeFromString<List<SourceDescriptor>>(sourcesRes.payloadJson)
            descriptors.shouldHaveSize(2)
            descriptors.map { it.id }.toSet() shouldBe setOf(1001L, 2002L)

            val pop1 = engine.handleRequest(
                IpcRequest(2, IpcCommands.GET_POPULAR, json.encodeToString(GetPagePayload(1001L, 1))),
            )
            pop1.success shouldBe true

            val pop2 = engine.handleRequest(
                IpcRequest(3, IpcCommands.GET_POPULAR, json.encodeToString(GetPagePayload(2002L, 1))),
            )
            pop2.success shouldBe true
        }
    }

    @Test
    fun `engine instantiates a multi source factory once per extension load`(@TempDir tempDir: Path) {
        CountingSourceFactory.invocations = 0
        val manifest = mihon.extension.model.ExtensionManifest(
            id = "ext.factory",
            name = "Factory",
            version = "1.0.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "all",
            sources = listOf(
                SourceDescriptor(1001L, "Test Source", "en", CountingSourceFactory::class.java.name),
                SourceDescriptor(2002L, "Test Source 2", "en", CountingSourceFactory::class.java.name),
            ),
        )
        val mext = tempDir.resolve("factory.mext").toFile()
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(mext)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zos.write(json.encodeToString(manifest).toByteArray())
            zos.closeEntry()
        }

        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        val sources = engine.loadExtension(mext, tempDir.resolve("work").toFile())

        CountingSourceFactory.invocations shouldBe 1
        sources.map { it.id }.toSet() shouldBe setOf(1001L, 2002L)
    }
}
