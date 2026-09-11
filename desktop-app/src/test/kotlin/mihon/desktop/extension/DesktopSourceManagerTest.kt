package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopSourceManagerTest {

    @Test
    fun `registers bundled MangaDex source and retrieves it by id`(@TempDir tempDir: Path) {
        val dummyProcessManager = WindowsExtensionProcessManager(tempDir.toFile())
        val manager = DesktopSourceManager(installer = null, processManager = dummyProcessManager)

        val sources = manager.getSources()
        sources.any { it.id == BundledMangaDexSource.MANGADEX_SOURCE_ID } shouldBe true

        val mangaDex = manager.findSourceDescriptor(BundledMangaDexSource.MANGADEX_SOURCE_ID)
        mangaDex shouldNotBe null
        mangaDex?.name shouldBe "MangaDex"
    }

    @Test
    fun `registers bundled local source with stable id and local marker`() {
        val manager = DesktopSourceManager(installer = null, processManager = null)

        val local = manager.findSourceDescriptor(BundledLocalSource.ID)
        local shouldNotBe null
        local?.name shouldBe BundledLocalSource.NAME
        local?.lang shouldBe BundledLocalSource.LANG
        local?.supportsLatest shouldBe true

        manager.getSourceStates().first { it.source.id == BundledLocalSource.ID }.isLocal shouldBe true
        manager.getFilterList(BundledLocalSource.ID).isEmpty() shouldBe false
    }

    @Test
    fun `local source lists searches and returns chapters for imported local manga`(@TempDir tempDir: Path) {
        val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("library.db"))
        try {
            val alphaId = db.insertManga(
                MangaRecord(
                    sourceId = BundledLocalSource.ID,
                    url = "local:alpha",
                    title = "Alpha",
                ),
            )
            val betaId = db.insertManga(
                MangaRecord(
                    sourceId = BundledLocalSource.ID,
                    url = "local:beta",
                    title = "Beta",
                ),
            )
            db.insertChapter(
                ChapterRecord(
                    mangaId = alphaId,
                    url = "local:alpha/1",
                    name = "Chapter 1",
                ),
            )
            db.insertChapter(
                ChapterRecord(
                    mangaId = betaId,
                    url = "local:beta/1",
                    name = "Chapter 1",
                ),
            )

            val manager = DesktopSourceManager(
                installer = null,
                processManager = null,
                libraryRepository = db,
            )

            val popular = runBlocking { manager.getPopular(BundledLocalSource.ID, 1) }
            popular.mangas.map { it.title } shouldBe listOf("Alpha", "Beta")
            popular.hasNextPage shouldBe false

            val search = runBlocking { manager.searchManga(BundledLocalSource.ID, 1, "bet", FilterList()) }
            search.mangas.map { it.title } shouldBe listOf("Beta")

            val chapters = runBlocking {
                manager.getChapterList(BundledLocalSource.ID, popular.mangas.first())
            }
            chapters.map { it.name } shouldBe listOf("Chapter 1")
        } finally {
            db.close()
        }
    }

    @Test
    fun `throws when querying unknown source with no process manager`() {
        val manager = DesktopSourceManager(installer = null, processManager = null)

        runBlocking {
            assertThrows<IllegalStateException> {
                manager.getPopular(999999L, 1)
            }
        }
    }

    @Test
    fun `ensureSourceLoaded dynamically loads package and reloads on process epoch restart`(@TempDir tempDir: Path) {
        runBlocking {
            val prefStore = mihon.desktop.preferences.DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installRoot = tempDir.resolve("extensions").toFile()
            val installer = DesktopExtensionInstaller(installRoot, prefStore)

            val manifest = mihon.extension.model.ExtensionManifest(
                id = "ext.sample.lazy",
                name = "Lazy Sample",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    mihon.extension.model.SourceDescriptor(
                        id = 7777L,
                        name = "Lazy Source",
                        lang = "en",
                        className = "ext.sample.LazySource",
                    ),
                ),
            )

            val mextFile = tempDir.resolve("lazy.mext").toFile()
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(mextFile)).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zos.write(kotlinx.serialization.json.Json.encodeToString(manifest).toByteArray())
                zos.closeEntry()
            }

            installer.installFromLocalFile(mextFile, trustOnInstall = true)

            var loadedCount = 0
            val processWorkDir = tempDir.resolve("work").toFile()
            val customProcManager = object : WindowsExtensionProcessManager(processWorkDir) {
                override suspend fun loadExtension(
                    packageFile: java.io.File,
                ): List<mihon.extension.model.SourceDescriptor> {
                    loadedCount++
                    kotlinx.coroutines.delay(50)
                    return manifest.sources
                }
            }

            val manager = DesktopSourceManager(installer = installer, processManager = customProcManager)

            // Sources list includes 7777L from installer manifest
            val sources = manager.getSources()
            sources.any { it.id == 7777L } shouldBe true

            // First call to ensureSourceLoaded loads package
            kotlinx.coroutines.coroutineScope {
                List(8) { async { manager.ensureSourceLoaded(7777L) } }.forEach { it.await() }
            }
            loadedCount shouldBe 1

            // Second call with same epoch does not reload
            manager.ensureSourceLoaded(7777L)
            loadedCount shouldBe 1

            // Unloading extension causes reload on next call
            manager.unloadExtension("ext.sample.lazy")
            manager.ensureSourceLoaded(7777L)
            loadedCount shouldBe 2
            manager.close()
        }
    }

    @Test
    fun `per-source enabled and incognito settings persist and disabled sources are filtered`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val manager = DesktopSourceManager(installer = null, processManager = null, preferenceStore = prefStore)
        val sourceId = BundledMangaDexSource.MANGADEX_SOURCE_ID

        manager.getSources().any { it.id == sourceId } shouldBe true
        manager.isSourceEnabled(sourceId) shouldBe true
        manager.isSourceIncognito(sourceId) shouldBe false

        manager.setSourceEnabled(sourceId, false)
        manager.getSources().none { it.id == sourceId } shouldBe true
        manager.getSourceStates().first { it.source.id == sourceId }.isEnabled shouldBe false

        manager.setSourceIncognito(sourceId, true)
        manager.getSourceStates().first { it.source.id == sourceId }.isIncognito shouldBe true

        val reopened = DesktopSourceManager(installer = null, processManager = null, preferenceStore = prefStore)
        reopened.isSourceEnabled(sourceId) shouldBe false
        reopened.isSourceIncognito(sourceId) shouldBe true
        reopened.getSources().none { it.id == sourceId } shouldBe true

        reopened.setSourceEnabled(sourceId, true)
        reopened.getSources().any { it.id == sourceId } shouldBe true
    }

    @Test
    fun `disabled extensions are filtered while per-source state remains available for details`(
        @TempDir tempDir: Path,
    ) {
        runBlocking {
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), prefStore)
            val manifest = ExtensionManifest(
                id = "ext.settings",
                name = "Settings Extension",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(
                        id = 6001L,
                        name = "Settings Source",
                        lang = "en",
                        className = "ext.SettingsSource",
                    ),
                ),
                capabilities = listOf(
                    "source_preferences",
                    "pref:string:apiKey:API Key",
                    "pref:boolean:dataSaver:Data Saver",
                ),
            )
            val mextFile = tempDir.resolve("settings.mext").toFile()
            writeMext(mextFile, manifest)
            installer.installFromLocalFile(mextFile, trustOnInstall = true)

            val manager = DesktopSourceManager(installer = installer, processManager = null)
            manager.getSources().any { it.id == 6001L } shouldBe true
            manager.getSourceStatesForExtension("ext.settings").single().isEnabled shouldBe true

            installer.setExtensionEnabled("ext.settings", false)
            manager.getSources().none { it.id == 6001L } shouldBe true
            manager.getSourceStatesForExtension("ext.settings").single().source.id shouldBe 6001L

            manager.setSourceEnabled(6001L, false)
            installer.setExtensionEnabled("ext.settings", true)
            manager.getSources().none { it.id == 6001L } shouldBe true
            manager.getSourceStatesForExtension("ext.settings").single().isEnabled shouldBe false

            manager.setSourceEnabled(6001L, true)
            manager.setSourceIncognito(6001L, true)
            manager.setExtensionIncognito("ext.settings", true)
            manager.getSourceStatesForExtension("ext.settings").single().isIncognito shouldBe true
            manager.isExtensionIncognito("ext.settings") shouldBe true
        }
    }

    @Test
    fun `extension capabilities surface configurable source definitions and values persist`(@TempDir tempDir: Path) {
        runBlocking {
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), prefStore)
            val manifest = ExtensionManifest(
                id = "ext.capabilities",
                name = "Capability Extension",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(
                        id = 6100L,
                        name = "Capability Source",
                        lang = "en",
                        className = "ext.CapabilitySource",
                    ),
                ),
                capabilities = listOf(
                    "source_preferences",
                    "pref:string:apiKey:API Key",
                    "pref:boolean:dataSaver:Data Saver",
                ),
            )
            val mextFile = tempDir.resolve("capabilities.mext").toFile()
            writeMext(mextFile, manifest)
            installer.installFromLocalFile(mextFile, trustOnInstall = true)

            val manager = DesktopSourceManager(installer = installer, processManager = null)
            manager.isSourceConfigurable(6100L) shouldBe true
            manager.getSourcePreferenceDefinitions(6100L).map { it.key } shouldBe listOf("apiKey", "dataSaver")

            manager.setSourcePreferenceValue(6100L, "apiKey", "secret")
            manager.setSourcePreferenceBoolean(6100L, "dataSaver", true)

            val reopened = DesktopSourceManager(installer = installer, processManager = null)
            reopened.getSourcePreferenceValue(6100L, "apiKey") shouldBe "secret"
            reopened.getSourcePreferenceBoolean(6100L, "dataSaver", false) shouldBe true
        }
    }

    @Test
    fun `builtin configurable source exposes definitions and persists values`(@TempDir tempDir: Path) {
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val manager = DesktopSourceManager(installer = null, processManager = null, preferenceStore = prefStore)
        val source = ConfigurableTestSource()
        manager.registerBuiltinSource(source)

        manager.isSourceConfigurable(source.id) shouldBe true
        manager.getSourcePreferenceDefinitions(source.id).map { it.key } shouldBe listOf("dataSaver", "apiKey")

        manager.setSourcePreferenceBoolean(source.id, "dataSaver", true)
        manager.setSourcePreferenceValue(source.id, "apiKey", "token")

        val reopened = DesktopSourceManager(installer = null, processManager = null, preferenceStore = prefStore)
        reopened.registerBuiltinSource(ConfigurableTestSource())
        reopened.getSourcePreferenceBoolean(source.id, "dataSaver", false) shouldBe true
        reopened.getSourcePreferenceValue(source.id, "apiKey") shouldBe "token"
    }

    @Test
    fun `clear cookies removes extension declared domain cookies`(@TempDir tempDir: Path) {
        runBlocking {
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), prefStore)
            val manifest = ExtensionManifest(
                id = "ext.cookies",
                name = "Cookie Extension",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(
                        id = 6200L,
                        name = "Cookie Source",
                        lang = "en",
                        className = "ext.CookieSource",
                    ),
                ),
                declaredDomains = listOf("api.sample.com"),
            )
            val mextFile = tempDir.resolve("cookies.mext").toFile()
            writeMext(mextFile, manifest)
            installer.installFromLocalFile(mextFile, trustOnInstall = true)

            val cookieStore = DesktopCookieStore(tempDir.resolve("cookies.json"))
            cookieStore.setCookies("api.sample.com", mapOf("sid" to "abc"))

            val manager = DesktopSourceManager(
                installer = installer,
                processManager = null,
                cookieStore = cookieStore,
            )
            manager.clearExtensionCookies("ext.cookies") shouldBe 1
            cookieStore.getDomainConfig("api.sample.com") shouldBe null
        }
    }

    @Test
    fun `clear cookies uses builtin base url host`(@TempDir tempDir: Path) {
        val cookieStore = DesktopCookieStore(tempDir.resolve("cookies.json"))
        cookieStore.setCookies("api.mangadex.org", mapOf("cf_clearance" to "token"))

        val manager = DesktopSourceManager(installer = null, processManager = null, cookieStore = cookieStore)
        manager.clearSourceCookies(BundledMangaDexSource.MANGADEX_SOURCE_ID) shouldBe 1
        cookieStore.getDomainConfig("api.mangadex.org") shouldBe null
    }

    private fun writeMext(file: File, manifest: ExtensionManifest) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(Json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
    }

    private class ConfigurableTestSource : DesktopConfigurableSource {
        override val id: Long = 7001L
        override val name: String = "Configurable Test Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun getPreferenceDefinitions(): List<SourcePreferenceDefinition> = listOf(
            SourcePreferenceDefinition(
                key = "dataSaver",
                title = "Data Saver",
                type = SourcePreferenceType.Boolean,
                defaultValue = "false",
            ),
            SourcePreferenceDefinition(
                key = "apiKey",
                title = "API Key",
                type = SourcePreferenceType.String,
                defaultValue = "",
            ),
        )

        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun searchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getMangaDetails(manga: SManga): SManga = manga

        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
