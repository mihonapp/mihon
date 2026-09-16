package mihon.desktop.ui.browse

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopNetworkHelper
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.ExtensionManifest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BrowsePresenterTest {

    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `counts only newer installed extension versions as pending`() {
        val installed = listOf(
            InstalledExtension(
                pkg = "ext.one",
                manifest = ExtensionManifest("ext.one", "One", "1.0", 10L, 1.4, "en", sources = emptyList()),
                installDir = "one",
                packageFile = "one.jar",
                installedAt = 0L,
            ),
        )
        val available = listOf(
            ExtensionStoreItem("ext.one", "One", "1.1", 11L),
            ExtensionStoreItem("ext.two", "Two", "1.0", 1L),
        )

        countPendingExtensionUpdates(installed, available) shouldBe 1
    }

    @Test
    fun `initializes state and toggles pinned source`(@TempDir tempDir: Path) = runBlocking {
        val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("test.db"))
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val networkHelper = DesktopNetworkHelper()
        val dummyProcessManager = WindowsExtensionProcessManager(tempDir.toFile())
        val installer = DesktopExtensionInstaller(
            installRoot = tempDir.resolve("exts").toFile(),
            preferenceStore = prefStore,
        )
        val storeService = ExtensionStoreService(preferenceStore = prefStore)
        val sourceManager = DesktopSourceManager(installer = installer, processManager = dummyProcessManager)

        val presenter = BrowsePresenter(
            sourceManager = sourceManager,
            installer = installer,
            storeService = storeService,
            libraryRepository = db,
            preferenceStore = prefStore,
            scope = scope,
        )

        val mangadexId = BundledMangaDexSource.MANGADEX_SOURCE_ID
        presenter.state.value.pinnedSourceIds shouldNotContain mangadexId

        presenter.togglePinSource(mangadexId)
        presenter.state.value.pinnedSourceIds shouldContain mangadexId

        presenter.togglePinSource(mangadexId)
        presenter.state.value.pinnedSourceIds shouldNotContain mangadexId

        // Tab navigation
        presenter.setTab(BrowseTab.Migration)
        presenter.state.value.selectedTab shouldBe BrowseTab.Migration

        presenter.setTab(BrowseTab.Extensions)
        presenter.state.value.selectedTab shouldBe BrowseTab.Extensions

        // Global search modal
        presenter.openGlobalSearch()
        presenter.state.value.isGlobalSearchOpen shouldBe true

        presenter.setGlobalSearchQuery("One Piece")
        presenter.state.value.globalSearchQuery shouldBe "One Piece"

        presenter.closeGlobalSearch()
        presenter.state.value.isGlobalSearchOpen shouldBe false

        db.close()
    }

    @Test
    fun `source enable disable and incognito update presenter state and persisted settings`(@TempDir tempDir: Path) =
        runBlocking {
            val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("test.db"))
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(
                installRoot = tempDir.resolve("exts").toFile(),
                preferenceStore = prefStore,
            )
            val sourceManager = DesktopSourceManager(
                installer = installer,
                processManager = null,
                preferenceStore = prefStore,
            )
            sourceManager.registerBuiltinSource(BundledMangaDexSource())
            val presenter = BrowsePresenter(
                sourceManager = sourceManager,
                installer = installer,
                storeService = ExtensionStoreService(preferenceStore = prefStore),
                libraryRepository = db,
                preferenceStore = prefStore,
                scope = scope,
            )
            val sourceId = BundledMangaDexSource.MANGADEX_SOURCE_ID

            presenter.setSourceEnabled(sourceId, false)
            presenter.state.value.sources.none { it.id == sourceId } shouldBe true
            presenter.state.value.sourceStates.first { it.source.id == sourceId }.isEnabled shouldBe false
            prefStore.property("extension.source.enabled.$sourceId") shouldBe "false"

            presenter.setSourceIncognito(sourceId, true)
            presenter.state.value.sourceStates.first { it.source.id == sourceId }.isIncognito shouldBe true
            prefStore.property("extension.source.incognito.$sourceId") shouldBe "true"

            presenter.setSourceEnabled(sourceId, true)
            presenter.state.value.sources.any { it.id == sourceId } shouldBe true

            db.close()
        }

    @Test
    fun `extension incognito persists and updates presenter state`(@TempDir tempDir: Path) = runBlocking {
        val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("test.db"))
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("exts").toFile(), prefStore)
        val sourceManager = DesktopSourceManager(
            installer = installer,
            processManager = null,
            preferenceStore = prefStore,
        )
        val presenter = BrowsePresenter(
            sourceManager = sourceManager,
            installer = installer,
            storeService = ExtensionStoreService(preferenceStore = prefStore),
            libraryRepository = db,
            preferenceStore = prefStore,
            scope = scope,
        )

        presenter.setExtensionIncognito("ext.test.sample", true)
        presenter.state.value.incognitoExtensionPackages shouldContain "ext.test.sample"
        prefStore.property("extension.incognito.ext.test.sample") shouldBe "true"

        presenter.toggleExtensionIncognito("ext.test.sample")
        presenter.state.value.incognitoExtensionPackages shouldNotContain "ext.test.sample"

        db.close()
    }

    @Test
    fun `source preference values update presenter state and persist`(@TempDir tempDir: Path) = runBlocking {
        val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("test.db"))
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("exts").toFile(), prefStore)
        val sourceManager = DesktopSourceManager(
            installer = installer,
            processManager = null,
            preferenceStore = prefStore,
        )
        val presenter = BrowsePresenter(
            sourceManager = sourceManager,
            installer = installer,
            storeService = ExtensionStoreService(preferenceStore = prefStore),
            libraryRepository = db,
            preferenceStore = prefStore,
            scope = scope,
        )

        presenter.setSourcePreferenceValue(4242L, "apiKey", "secret")
        presenter.state.value.sourcePreferenceValues[4242L]?.get("apiKey") shouldBe "secret"
        prefStore.property("extension.source.preference.4242.apiKey") shouldBe "secret"

        db.close()
    }

    @Test
    fun `clear cookies through presenter delegates to source manager`(@TempDir tempDir: Path) = runBlocking {
        val db = DesktopLibraryDatabaseFactory.open(tempDir.resolve("test.db"))
        val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("exts").toFile(), prefStore)
        val cookieStore = mihon.desktop.extension.DesktopCookieStore(tempDir.resolve("cookies.json"))
        cookieStore.setCookies("api.mangadex.org", mapOf("cf_clearance" to "token"))
        val sourceManager = DesktopSourceManager(
            installer = installer,
            processManager = null,
            preferenceStore = prefStore,
            cookieStore = cookieStore,
        )
        sourceManager.registerBuiltinSource(BundledMangaDexSource())
        val presenter = BrowsePresenter(
            sourceManager = sourceManager,
            installer = installer,
            storeService = ExtensionStoreService(preferenceStore = prefStore),
            libraryRepository = db,
            preferenceStore = prefStore,
            scope = scope,
        )

        presenter.clearSourceCookies(BundledMangaDexSource.MANGADEX_SOURCE_ID)
        cookieStore.getDomainConfig("api.mangadex.org") shouldBe null

        db.close()
    }
}
