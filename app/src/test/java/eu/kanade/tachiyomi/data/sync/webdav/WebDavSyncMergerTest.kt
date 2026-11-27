package eu.kanade.tachiyomi.data.sync.webdav

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class WebDavSyncMergerTest {

    private val allOptions = WebDavSyncOptions(
        syncLibrary = true,
        syncExtensionStores = true,
        syncExtensions = true,
    )

    @Test
    fun `first sync unions manga categories stores and extensions without data loss`() {
        val local = WebDavSyncSnapshot(
            categories = listOf(SyncCategory("Action", 0, 0)),
            manga = listOf(
                SyncManga(
                    source = 1L,
                    url = "/a",
                    title = "Manga A",
                    favoriteAt = 100L,
                    categories = listOf("Action"),
                    chapters = listOf(SyncChapter(url = "/a/1", name = "Ch 1", read = true, lastPageRead = 20)),
                    history = listOf(SyncHistory(chapterUrl = "/a/1", readAt = 1000L, readDuration = 5000L)),
                ),
            ),
            extensionStores = listOf(SyncExtensionStore("https://store-a/index.json", "Store A", signingKey = "keyA")),
            installedExtensions = listOf(SyncInstalledExtension("ext.a", "Ext A", "en", 1, "1.0")),
        )
        val remote = WebDavSyncSnapshot(
            categories = listOf(SyncCategory("Comedy", 0, 0)),
            manga = listOf(
                SyncManga(
                    source = 2L,
                    url = "/b",
                    title = "Manga B",
                    favoriteAt = 200L,
                    categories = listOf("Comedy"),
                    chapters = listOf(SyncChapter(url = "/b/1", name = "Ch 1", bookmark = true, lastPageRead = 5)),
                    history = listOf(SyncHistory(chapterUrl = "/b/1", readAt = 2000L, readDuration = 3000L)),
                ),
            ),
            extensionStores = listOf(SyncExtensionStore("https://store-b/index.json", "Store B", signingKey = "keyB")),
            installedExtensions = listOf(SyncInstalledExtension("ext.b", "Ext B", "zh", 2, "2.0")),
        )

        val merged = WebDavSyncMerger.merge(
            base = null,
            local = local,
            remote = remote,
            options = allOptions,
        )

        merged.categories.map { it.name } shouldContainExactly listOf("Action", "Comedy")
        merged.manga.map { it.title } shouldContainExactly listOf("Manga A", "Manga B")
        merged.extensionStores.map { it.indexUrl } shouldContainExactly listOf(
            "https://store-a/index.json",
            "https://store-b/index.json",
        )
        merged.installedExtensions.map { it.pkgName } shouldContainExactly listOf("ext.a", "ext.b")
    }

    @Test
    fun `unfavoriting on one device propagates when remote is unchanged`() {
        val baseManga = SyncManga(
            source = 1L,
            url = "/a",
            title = "Manga A",
            favoriteAt = 100L,
            chapters = listOf(SyncChapter(url = "/a/1", name = "Ch 1", read = false)),
        )
        val base = WebDavSyncSnapshot(manga = listOf(baseManga))
        val local = WebDavSyncSnapshot(manga = emptyList())
        val remote = WebDavSyncSnapshot(manga = listOf(baseManga))

        val merged = WebDavSyncMerger.merge(base = base, local = local, remote = remote, options = allOptions)

        merged.manga shouldBe emptyList()
    }

    @Test
    fun `marking chapter unread and removing bookmark propagates when remote is unchanged`() {
        val baseChapter = SyncChapter(url = "/a/1", name = "Ch 1", read = true, bookmark = true, lastPageRead = 15)
        val baseManga = SyncManga(
            source = 1L,
            url = "/a",
            title = "Manga A",
            favoriteAt = 100L,
            chapters = listOf(baseChapter),
        )
        val localManga = baseManga.copy(
            chapters = listOf(baseChapter.copy(read = false, bookmark = false, lastPageRead = 0)),
        )

        val merged = WebDavSyncMerger.merge(
            base = WebDavSyncSnapshot(manga = listOf(baseManga)),
            local = WebDavSyncSnapshot(manga = listOf(localManga)),
            remote = WebDavSyncSnapshot(manga = listOf(baseManga)),
            options = allOptions,
        )

        val mergedChapter = merged.manga.single().chapters.single()
        mergedChapter.read shouldBe false
        mergedChapter.bookmark shouldBe false
        mergedChapter.lastPageRead shouldBe 0
    }

    @Test
    fun `concurrent chapter progress conflict picks newer history timestamp and adds read duration`() {
        val baseChapter = SyncChapter(url = "/a/1", name = "Ch 1", read = false, lastPageRead = 5)
        val baseManga = SyncManga(
            source = 1L,
            url = "/a",
            title = "Manga A",
            favoriteAt = 100L,
            chapters = listOf(baseChapter),
            history = listOf(SyncHistory(chapterUrl = "/a/1", readAt = 1000L, readDuration = 10_000L)),
        )
        val localManga = baseManga.copy(
            chapters = listOf(baseChapter.copy(read = false, lastPageRead = 12)),
            history = listOf(SyncHistory(chapterUrl = "/a/1", readAt = 2000L, readDuration = 14_000L)),
        )
        val remoteManga = baseManga.copy(
            chapters = listOf(baseChapter.copy(read = true, lastPageRead = 30)),
            history = listOf(SyncHistory(chapterUrl = "/a/1", readAt = 3000L, readDuration = 16_000L)),
        )

        val merged = WebDavSyncMerger.merge(
            base = WebDavSyncSnapshot(manga = listOf(baseManga)),
            local = WebDavSyncSnapshot(manga = listOf(localManga)),
            remote = WebDavSyncSnapshot(manga = listOf(remoteManga)),
            options = allOptions,
        )

        val chapter = merged.manga.single().chapters.single()
        chapter.read shouldBe true
        chapter.lastPageRead shouldBe 30

        val history = merged.manga.single().history.single()
        history.readAt shouldBe 3000L
        // base 10_000 + local delta 4_000 + remote delta 6_000 = 20_000
        history.readDuration shouldBe 20_000L
    }

    @Test
    fun `pending missing extensions on fresh device are not deleted on subsequent syncs`() {
        val extA = SyncInstalledExtension("ext.a", "Ext A", "en", 1, "1.0")
        val extB = SyncInstalledExtension("ext.b", "Ext B", "zh", 1, "1.0")
        val base = WebDavSyncSnapshot(installedExtensions = listOf(extA, extB))
        val remote = WebDavSyncSnapshot(installedExtensions = listOf(extA, extB))
        // Device B only installed extA so far; extB was never installed here
        val local = WebDavSyncSnapshot(installedExtensions = listOf(extA))

        val mergedNotOwned = WebDavSyncMerger.merge(
            base = base,
            local = local,
            remote = remote,
            options = allOptions,
            lastLocalInstalledExtensionPkgs = setOf("ext.a"),
        )
        mergedNotOwned.installedExtensions.map { it.pkgName } shouldContainExactly listOf("ext.a", "ext.b")

        // Once Device B had extB installed previously and then uninstalled it, removal propagates
        val mergedExplicitUninstall = WebDavSyncMerger.merge(
            base = base,
            local = local,
            remote = remote,
            options = allOptions,
            lastLocalInstalledExtensionPkgs = setOf("ext.a", "ext.b"),
        )
        mergedExplicitUninstall.installedExtensions.map { it.pkgName } shouldContainExactly listOf("ext.a")
    }

    @Test
    fun `disabled sync options preserve existing remote sections`() {
        val remote = WebDavSyncSnapshot(
            extensionStores = listOf(SyncExtensionStore("https://store/index.json", "Store", signingKey = "key")),
            installedExtensions = listOf(SyncInstalledExtension("ext.a", "Ext A", "en", 1, "1.0")),
        )
        val local = WebDavSyncSnapshot(
            manga = listOf(SyncManga(source = 1L, url = "/m", title = "Local Manga", favoriteAt = 500L)),
        )

        val merged = WebDavSyncMerger.merge(
            base = null,
            local = local,
            remote = remote,
            options = WebDavSyncOptions(syncLibrary = true, syncExtensionStores = false, syncExtensions = false),
        )

        merged.manga.map { it.title } shouldContainExactly listOf("Local Manga")
        merged.extensionStores.map { it.indexUrl } shouldContainExactly listOf("https://store/index.json")
        merged.installedExtensions.map { it.pkgName } shouldContainExactly listOf("ext.a")
    }
}
