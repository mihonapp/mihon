package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaDetails
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MangaDetailScreenTest {
    @Test
    fun `wide library shows adjacent detail with real metadata and chapter semantics in repository order`() =
        runComposeUiTest {
            setScreen(width = 1280.dp)

            onNodeWithTag("library-grid-pane").assertExists()
            onNodeWithTag("manga-detail-pane").assertExists()
            val libraryBounds = onNodeWithTag("library-grid-pane").getBoundsInRoot()
            val detailBounds = onNodeWithTag("manga-detail-pane").getBoundsInRoot()
            (libraryBounds.right - libraryBounds.left > detailBounds.right - detailBounds.left) shouldBe true
            onNodeWithTag("manga-detail-title").assertTextContains("Real title")
            onNodeWithText("Author name").assertExists()
            onNodeWithText("Real description").assertExists()
            onNodeWithText("Drama · Mystery").assertExists()
            onNodeWithText("Favorites").assertExists()
            onNodeWithText("Private notes").assertExists()
            onAllNodesWithTag("chapter-row")[0].assertTextContains("Second in repository")
            onAllNodesWithTag("chapter-row")[1].assertTextContains("First in repository")
            onNodeWithText("Read · Bookmarked · Page 7").assertExists()
            onAllNodesWithTag("chapter-reader-action")[0].assertIsNotEnabled()
        }

    @Test
    fun `narrow selection pushes detail and back returns to library`() = runComposeUiTest {
        var selected: Long? = 7
        setScreen(width = 900.dp, onBack = { selected = null })

        onNodeWithTag("library-grid-pane").assertDoesNotExist()
        onNodeWithTag("manga-detail-pane").assertExists()
        onNodeWithTag("manga-detail-back").performClick()

        selected shouldBe null
    }

    @Test
    fun `missing detail does not render stale content`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MangaDetailScreen(MangaDetailUiState(manga = null, loading = false), onBack = {})
            }
        }

        onNodeWithText("Real title").assertDoesNotExist()
        onNodeWithTag("manga-detail-missing").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        width: androidx.compose.ui.unit.Dp,
        onBack: () -> Unit = {},
    ) {
        val item = LibraryManga(7, 107, "/7", "Real title", null, 2, 1, "Author name")
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width, 800.dp)) {
                    LibraryScreen(
                        state = LibraryUiState(false, items = listOf(item), selectedMangaId = 7),
                        detailState = detailState(),
                        onQueryChange = {},
                        onMangaSelected = {},
                        onBackFromDetail = onBack,
                        onImportBackup = {},
                        onImportLocal = {},
                    )
                }
            }
        }
    }

    private fun detailState() = MangaDetailUiState(
        manga = MangaDetails(
            7, 107, "/7", "Real title", null, "Author name", "Real description", "[\"Drama\",\"Mystery\"]",
            0, null, true, 0, 0, 0, "ALWAYS_UPDATE", 0, null, "[]", 0, "Private notes", true, "{}",
            listOf(CategoryRecord(1, "Favorites")),
        ),
        chapters = listOf(
            chapter(72, "Second in repository", read = true, bookmark = true, page = 7),
            chapter(71, "First in repository"),
        ),
        loading = false,
    )

    private fun chapter(id: Long, name: String, read: Boolean = false, bookmark: Boolean = false, page: Long = 0) =
        LibraryChapter(id, 7, "/$id", name, null, read, bookmark, page, 0, 0, 1.0, 0, 0, 0, "{}")
}
