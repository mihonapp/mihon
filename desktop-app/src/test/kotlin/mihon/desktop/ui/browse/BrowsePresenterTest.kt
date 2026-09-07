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
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.extension.WindowsExtensionProcessManager
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.desktop.library.db.DesktopLibraryDatabaseFactory
import mihon.desktop.preferences.DesktopPreferenceStore
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
}
