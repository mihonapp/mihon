package mihon.desktop.extension

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ExtensionStoreServiceTest {

    @Test
    fun `repository management persists custom repos in preference store`(@TempDir tempDir: Path) {
        val storeFile = tempDir.resolve("prefs.properties")
        val prefStore = DesktopPreferenceStore(storeFile)
        val service = ExtensionStoreService(prefStore)

        // Default repo present
        service.getRepositories() shouldBe listOf(ExtensionStoreService.DEFAULT_REPO)

        // Add repo
        service.addRepository("https://example.com/repo/")
        service.getRepositories() shouldContain "https://example.com/repo"

        // Remove repo
        service.removeRepository("https://example.com/repo")
        service.getRepositories() shouldBe listOf(ExtensionStoreService.DEFAULT_REPO)
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
        item.downloadUrl shouldBe "https://repo.mihon.app/extensions/manga-dex-v1.4.0.mext"
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
}
