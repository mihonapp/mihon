package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.DesktopRuntime
import mihon.desktop.extension.ExtensionStoreService
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BrowseNavigationTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `extension details back returns to extensions tab and preserves installed list`() = runComposeUiTest {
        val runtime = DesktopRuntime.forTesting()
        try {
            // Keep the presenter's repository refresh fast and offline.
            runtime.preferences.update {
                setProperty(ExtensionStoreService.PREF_KEY_REPOSITORIES, "http://127.0.0.1:9/repo")
            }

            val pkg = "ext.test.navigation"
            val manifest = ExtensionManifest(
                id = pkg,
                name = "Navigation Extension",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(
                        id = 8100L,
                        name = "Navigation Source",
                        lang = "en",
                        className = "ext.test.NavigationSource",
                    ),
                ),
            )
            val mextFile = Files.createTempFile("navigation-test", ".mext")
            try {
                ZipOutputStream(FileOutputStream(mextFile.toFile())).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(Json.encodeToString(manifest).toByteArray())
                    zip.closeEntry()
                }
                runBlocking {
                    runtime.extensionInstaller.installFromLocalFile(mextFile.toFile(), trustOnInstall = true)
                }

                setContent {
                    Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                        BrowseContentView(runtime = runtime, onReadChapter = {})
                    }
                }

                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithTag("browse-tab-extensions").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("browse-tab-extensions").performClick()

                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithTag("extension-item-$pkg").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("extension-item-$pkg").performClick()

                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithTag("extension-details-screen").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("extension-details-screen").assertIsDisplayed()

                onNodeWithTag("extension-details-back").performClick()

                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithTag("extensions-list").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("extensions-list").assertIsDisplayed()
                onNodeWithTag("browse-tab-extensions").assertIsSelected()
            } finally {
                Files.deleteIfExists(mextFile)
            }
        } finally {
            runBlocking { runtime.shutdown() }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `local source opens local detail and reads chapter through callback`() = runComposeUiTest {
        val runtime = DesktopRuntime.forTesting()
        val sourceDir = Files.createTempDirectory("local-browse-source")
        val chapterDir = Files.createDirectories(sourceDir.resolve("Chapter 1"))
        Files.writeString(chapterDir.resolve("page1.jpg"), "page")
        try {
            runBlocking {
                runtime.localImporter.import(sourceDir, runtime.localLibraryRoot, System.currentTimeMillis())
            }
            val mangaUrl = runBlocking {
                runtime.library.librarySnapshot(null).single().url
            }
            var readChapterId: Long? = null

            setContent {
                Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                    BrowseContentView(runtime = runtime, onReadChapter = { readChapterId = it })
                }
            }

            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag("source-item-0").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("source-item-0").performClick()

            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag("manga-grid").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("manga-card-$mangaUrl").performClick()

            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag("chapter-reader-action").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("chapter-reader-action").performClick()

            waitUntil(timeoutMillis = 15_000) { readChapterId != null }
        } finally {
            runBlocking { runtime.shutdown() }
            sourceDir.toFile().deleteRecursively()
        }
    }
}
