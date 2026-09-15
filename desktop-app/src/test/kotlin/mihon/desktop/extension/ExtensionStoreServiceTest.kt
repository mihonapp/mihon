package mihon.desktop.extension

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.preferences.DesktopPreferenceStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class ExtensionStoreServiceTest {

    @Test
    fun `repository management persists custom repos in preference store`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("prefs.properties")
        val prefStore = DesktopPreferenceStore(storeFile)
        val service = ExtensionStoreService(prefStore)

        service.getRepositories() shouldBe emptyList()

        // Add repo
        service.addRepository("https://example.com/repo/")
        service.getRepositories() shouldBe listOf("https://example.com/repo")
        ExtensionStoreService(DesktopPreferenceStore(storeFile)).getRepositories() shouldBe
            listOf("https://example.com/repo")

        // Remove repo
        service.removeRepository("https://example.com/repo")
        service.getRepositories() shouldBe emptyList()
        ExtensionStoreService(DesktopPreferenceStore(storeFile)).getRepositories() shouldBe emptyList()
    }

    @Test
    fun `fresh profile does not fetch any extension repository`(@TempDir tempDir: Path) = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            throw IOException("Unexpected repository request")
        }.build()
        val service = ExtensionStoreService(DesktopPreferenceStore(tempDir.resolve("prefs.properties")), client)

        service.fetchAvailableExtensions() shouldBe emptyList()
        requests.get() shouldBe 0
        Unit
    }

    @Test
    fun `blank saved repository list stays empty`(@TempDir tempDir: Path) {
        val store = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        store.update { setProperty(ExtensionStoreService.PREF_KEY_REPOSITORIES, " \n\t ") }

        ExtensionStoreService(store).getRepositories() shouldBe emptyList()
    }

    @Test
    fun `existing explicitly configured repositories are preserved`(@TempDir tempDir: Path) {
        val store = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        store.update {
            setProperty(
                ExtensionStoreService.PREF_KEY_REPOSITORIES,
                "https://example.com/first\nhttps://example.com/second",
            )
        }

        ExtensionStoreService(store).getRepositories() shouldBe
            listOf("https://example.com/first", "https://example.com/second")
    }

    @Test
    fun `parses standard repository index JSON and resolves URLs`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val sampleJson = """
        [
            {
                "name": "MangaDex",
                "pkg": "eu.kanade.tachiyomi.extension.all.mangadex",
                "apk": "manga-dex-v1.4.0.mext",
                "lang": "all",
                "code": 104,
                "version": "1.4.0",
                "nsfw": 0,
                "icon": "icon.png",
                "sha256": "abcdef123456",
                "declaredDomains": ["api.mangadex.org", "mangadex.org"],
                "sources": [
                    {
                        "id": 24992835730212389,
                        "name": "MangaDex",
                        "lang": "en",
                        "class": ".MangaDex",
                        "supportsLatest": true
                    }
                ]
            }
        ]
        """.trimIndent()

        val items = service.parseIndex(sampleJson, "https://repo.mihon.app/extensions")
        items.shouldHaveSize(1)
        val item = items[0]
        item.pkg shouldBe "eu.kanade.tachiyomi.extension.all.mangadex"
        item.name shouldBe "MangaDex"
        item.version shouldBe "1.4.0"
        item.versionCode shouldBe 104L
        item.downloadUrl shouldBe "https://repo.mihon.app/extensions/apk/manga-dex-v1.4.0.mext"
        item.iconUrl shouldBe "https://repo.mihon.app/extensions/icon.png"
        item.sha256 shouldBe "abcdef123456"
        item.declaredDomains shouldBe listOf("api.mangadex.org", "mangadex.org")
        item.sources.shouldHaveSize(1)
        item.sources[0].id shouldBe 24992835730212389L
        item.sources[0].className shouldBe ".MangaDex"
    }

    @Test
    fun `hides unsupported desktop meta packages from parsed store index`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val sampleJson = """
        [
            {
                "name": "Outdated App",
                "pkg": "eu.kanade.tachiyomi.extension.all.keiyoushi",
                "version": "1.4.1",
                "code": 104
            },
            {
                "name": "Update to Mihon 0.20.1+",
                "pkg": "eu.kanade.tachiyomi.extension.all.mihon",
                "version": "1.4.1",
                "code": 104
            },
            {
                "name": "MangaDex",
                "pkg": "eu.kanade.tachiyomi.extension.all.mangadex",
                "version": "1.4.1",
                "code": 104
            }
        ]
        """.trimIndent()

        val items = service.parseIndex(sampleJson, "https://repo.mihon.app/extensions")
        items.shouldHaveSize(1)
        items[0].pkg shouldBe "eu.kanade.tachiyomi.extension.all.mangadex"
    }

    @Test
    fun `deduplicates across repos selecting highest version code`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val repo1Json = """
        [{"pkg": "ext.test", "name": "Test Extension", "version": "1.0.0", "code": 1}]
        """.trimIndent()
        val repo2Json = """
        [{"pkg": "ext.test", "name": "Test Extension (Updated)", "version": "1.1.0", "code": 2}]
        """.trimIndent()

        val items1 = service.parseIndex(repo1Json, "https://repo1.com")
        val items2 = service.parseIndex(repo2Json, "https://repo2.com")

        val combined = (items1 + items2).groupBy { it.pkg }.map { (_, items) ->
            items.maxByOrNull { it.versionCode }!!
        }

        combined.shouldHaveSize(1)
        combined[0].versionCode shouldBe 2L
        combined[0].version shouldBe "1.1.0"
    }

    @Test
    fun `normalizes repo URLs with various index file suffixes`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        // Simulate adding repos with different URL formats
        service.addRepository("https://example.com/repo/index.min.json")
        service.addRepository("https://example.com/repo2/index.pb")
        service.addRepository("https://example.com/repo3/repo.json")
        service.addRepository("https://example.com/repo4/index.json")
        service.addRepository("https://example.com/repo5/")

        val repos = service.getRepositories()
        repos shouldContain "https://example.com/repo"
        repos shouldContain "https://example.com/repo2"
        repos shouldContain "https://example.com/repo3"
        repos shouldContain "https://example.com/repo4"
        repos shouldContain "https://example.com/repo5"
    }

    @Test
    fun `apk field produces download URL with apk subdirectory`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val sampleJson = """
        [{"pkg": "ext.test", "name": "Test", "version": "1.0.0", "code": 1, "apk": "tachiyomi-zh-copymanga-v1.4.84.apk"}]
        """.trimIndent()

        val items = service.parseIndex(sampleJson, "https://raw.githubusercontent.com/keiyoushi/extensions/repo")
        items.shouldHaveSize(1)
        items[0].downloadUrl shouldBe
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/tachiyomi-zh-copymanga-v1.4.84.apk"
    }

    @Test
    fun `downloadUrl field is used as-is without apk prefix`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val sampleJson = """
        [{"pkg": "ext.test", "name": "Test", "version": "1.0.0", "code": 1, "downloadUrl": "https://cdn.example.com/ext.mext"}]
        """.trimIndent()

        val items = service.parseIndex(sampleJson, "https://repo.example.com")
        items.shouldHaveSize(1)
        items[0].downloadUrl shouldBe "https://cdn.example.com/ext.mext"
    }

    @Test
    fun `icon URL defaults to icon subdirectory with pkg name when not specified`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val sampleJson = """
        [{"pkg": "eu.kanade.tachiyomi.extension.zh.copymanga", "name": "CopyManga", "version": "1.4.84", "code": 1, "apk": "copymanga.apk"}]
        """.trimIndent()

        val items = service.parseIndex(sampleJson, "https://raw.githubusercontent.com/keiyoushi/extensions/repo")
        items.shouldHaveSize(1)
        items[0].iconUrl shouldBe
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/icon/eu.kanade.tachiyomi.extension.zh.copymanga.png"
    }

    @Test
    fun `normalizes github raw and tree URLs to raw dot githubusercontent dot com`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        service.normalizeRepoUrl("https://github.com/keiyoushi/extensions/raw/repo/index.pb") shouldBe
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        service.normalizeRepoUrl("https://github.com/keiyoushi/extensions/tree/repo") shouldBe
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
    }

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun `parses synthetic protobuf repository index`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val store = DesktopNetworkExtensionStore(
            name = "Test Repo",
            badgeLabel = "Test",
            extensionList = DesktopNetworkExtensionStore.ExtensionList(
                extensions = listOf(
                    DesktopNetworkExtensionStore.Extension(
                        name = "Test Manga Extension",
                        packageName = "eu.kanade.tachiyomi.extension.en.testmanga",
                        versionName = "1.2.3",
                        versionCode = 42L,
                        extensionLib = "1.4",
                        resources = DesktopNetworkExtensionStore.Resources(
                            apkUrl = "https://example.com/test.apk",
                            iconUrl = "https://example.com/test.png",
                        ),
                        contentWarning = DesktopNetworkExtensionStore.ContentWarning.SAFE,
                        sources = listOf(
                            DesktopNetworkExtensionStore.Source(
                                id = 12345L,
                                name = "Test Source",
                                language = "en",
                            ),
                        ),
                    ),
                    DesktopNetworkExtensionStore.Extension(
                        name = "Hidden App",
                        packageName = "eu.kanade.tachiyomi.extension.all.keiyoushi",
                        versionName = "1.0.0",
                    ),
                ),
            ),
        )

        val encoded = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(
            DesktopNetworkExtensionStore.serializer(),
            store,
        )
        val items = service.parseProtobufIndex(encoded, "https://example.com")

        items.shouldHaveSize(1)
        val first = items.first()
        first.pkg shouldBe "eu.kanade.tachiyomi.extension.en.testmanga"
        first.name shouldBe "Test Manga Extension"
        first.version shouldBe "1.2.3"
        first.versionCode shouldBe 42L
        first.downloadUrl shouldBe "https://example.com/test.apk"
        first.iconUrl shouldBe "https://example.com/test.png"
        first.lang shouldBe "en"
        first.isNsfw shouldBe false
        first.sources.shouldHaveSize(1)
        first.sources.first().id shouldBe 12345L
    }

    @Test
    fun `parses real keiyoushi index pb if present`(@TempDir tempDir: Path) {
        val pbFile = java.io.File(System.getenv("TEMP"), "keiyoushi_index.pb")
        if (!pbFile.exists()) return
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)
        val decompressed = java.util.zip.GZIPInputStream(pbFile.inputStream()).use { it.readBytes() }
        val items = service.parseProtobufIndex(
            decompressed,
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
        )
        items.shouldNotBeEmpty()
        items.size shouldBeGreaterThan 1000
    }

    @Test
    fun `fetches live keiyoushi repo using index pb URL`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getenv("MIHON_W_LIVE_TESTS") == "1",
            "Set MIHON_W_LIVE_TESTS=1 to run network-dependent live tests",
        )
        val tempDir = java.nio.file.Files.createTempDirectory("mihon_test_prefs")
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)
        kotlinx.coroutines.runBlocking {
            val items = service.fetchRepository("https://github.com/keiyoushi/extensions/raw/repo/index.pb")
            println("LIVE_TEST: Successfully fetched ${items.size} extensions from live keiyoushi repo!")
            items.shouldNotBeEmpty()
            items.size shouldBeGreaterThan 1000
        }
    }

    @Test
    fun `parses signing keys from index and applies repository default`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val service = ExtensionStoreService(prefStore)

        val indexJson = """
        [
            {
                "pkg": "ext.one",
                "name": "One",
                "version": "1.0.0",
                "code": 1,
                "signingKey": "AA:BB:CC"
            },
            {
                "pkg": "ext.two",
                "name": "Two",
                "version": "1.0.0",
                "code": 1,
                "signingKeyFingerprint": "DDEEFF"
            },
            {
                "pkg": "ext.three",
                "name": "Three",
                "version": "1.0.0",
                "code": 1
            }
        ]
        """.trimIndent()

        val items = service.parseIndex(indexJson, "https://repo.example/extensions", defaultSigningKey = "112233")
        items shouldHaveSize 3
        items.first { it.pkg == "ext.one" }.signingKey shouldBe "AA:BB:CC"
        items.first { it.pkg == "ext.two" }.signingKey shouldBe "DDEEFF"
        items.first { it.pkg == "ext.three" }.signingKey shouldBe "112233"
    }

    @Test
    fun `fetchRepository applies repo json signing key to legacy index`(@TempDir tempDir: Path) {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/repo/index.min.json") { exchange ->
            val body = """
                [{"pkg":"ext.legacy","name":"Legacy","version":"1.0.0","code":1}]
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/repo/repo.json") { exchange ->
            val body = """{"meta":{"signingKeyFingerprint":"AABBCCDD"}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val service = ExtensionStoreService(DesktopPreferenceStore(tempDir.resolve("prefs.properties")))
            val items = kotlinx.coroutines.runBlocking {
                service.fetchRepository("http://127.0.0.1:${server.address.port}/repo")
            }
            items shouldHaveSize 1
            items.single().signingKey shouldBe "AABBCCDD"
        } finally {
            server.stop(0)
        }
    }
}
