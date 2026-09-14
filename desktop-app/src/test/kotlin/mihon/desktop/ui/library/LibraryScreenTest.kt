package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class LibraryScreenTest {
    @Test
    fun `renders persisted manga metadata unread marker and stable tags`() = runComposeUiTest {
        setLibraryContent(
            LibraryUiState(
                loading = false,
                items = listOf(manga(42, "Witch Hat Atelier", chapters = 18, unread = 4)),
            ),
        )

        onNodeWithTag("library-screen").assertExists()
        onNodeWithTag("library-search").assertExists()
        onNodeWithTag("library-item-42").assertExists()
        onNodeWithText("Witch Hat Atelier").assertExists()
        onNodeWithText("Source 142 · 18 chapters").assertExists()
        onNodeWithText("4 unread").assertExists()
    }

    @Test
    fun `clicking manga reports its exact database id`() = runComposeUiTest {
        var selected: Long? = null
        setLibraryContent(
            state = LibraryUiState(loading = false, items = listOf(manga(73, "Selected"))),
            onMangaSelected = { selected = it },
        )

        onNodeWithTag("library-item-73").performClick()

        selected shouldBe 73L
    }

    @Test
    fun `comfortable card uses readable cover led desktop geometry`() = runComposeUiTest {
        val title = "A deliberately long desktop manga title"
        setLibraryContent(
            state = LibraryUiState(loading = false, items = listOf(manga(42, title, chapters = 18, unread = 4))),
            rootWidth = 520.dp,
        )

        val cardBounds = onNodeWithTag("library-item-42").fetchSemanticsNode().boundsInRoot
        val titleBounds = onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        check(cardBounds.height > cardBounds.width) { "Expected portrait card, got $cardBounds" }
        check(titleBounds.width > 120f) { "Expected readable title width, got $titleBounds" }
    }

    @Test
    fun `library controls stay inside a narrow desktop window`() = runComposeUiTest {
        setLibraryContent(
            state = LibraryUiState(loading = false, items = listOf(manga(42, "Narrow window"))),
            rootWidth = 480.dp,
        )

        val rootBounds = onNodeWithTag("library-screen").fetchSemanticsNode().boundsInRoot
        listOf(
            "library-import-backup",
            "library-import-local",
            "display-mode-ComfortableGrid",
            "display-mode-CompactGrid",
            "display-mode-CoverOnly",
            "display-mode-List",
            "library-grid-slider",
            "library-filter-sort-button",
            "library-toggle-selection",
            "library-manage-categories-button",
            "library-search",
        ).forEach { tag ->
            val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= rootBounds.left, "$tag starts outside $rootBounds: $bounds")
            assertTrue(bounds.right <= rootBounds.right, "$tag ends outside $rootBounds: $bounds")
        }
    }

    @Test
    fun `empty database offers both import actions`() = runComposeUiTest {
        var backupClicks = 0
        var localClicks = 0
        setLibraryContent(
            state = LibraryUiState(loading = false),
            onImportBackup = { backupClicks++ },
            onImportLocal = { localClicks++ },
        )

        onNodeWithText("Your library is empty").assertExists()
        onNodeWithTag("library-import-backup").performClick()
        onNodeWithTag("library-import-local").performClick()
        backupClicks shouldBe 1
        localClicks shouldBe 1
    }

    @Test
    fun `empty search result is distinct from an empty database`() = runComposeUiTest {
        setLibraryContent(LibraryUiState(loading = false, query = "missing"))

        onNodeWithText("No manga match “missing”").assertExists()
    }

    @Test
    fun `error message and retry action are visible`() = runComposeUiTest {
        var retries = 0
        setLibraryContent(
            state = LibraryUiState(loading = false, errorMessage = "database unavailable"),
            onRetry = { retries++ },
        )

        onNodeWithTag("library-error").assertTextContains("database unavailable")
        onNodeWithTag("library-retry").performClick()
        retries shouldBe 1
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setLibraryContent(
        state: LibraryUiState,
        onMangaSelected: (Long) -> Unit = {},
        onImportBackup: () -> Unit = {},
        onImportLocal: () -> Unit = {},
        onRetry: () -> Unit = {},
        rootWidth: Dp = 1000.dp,
    ) {
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredSize(rootWidth, 800.dp)) {
                    LibraryScreen(
                        state = state,
                        onQueryChange = {},
                        onMangaSelected = onMangaSelected,
                        onImportBackup = onImportBackup,
                        onImportLocal = onImportLocal,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }

    private fun manga(
        id: Long,
        title: String,
        chapters: Long = 1,
        unread: Long = 0,
    ) = LibraryManga(
        id = id,
        sourceId = id + 100,
        url = "/$id",
        title = title,
        thumbnailUrl = null,
        chapterCount = chapters,
        unreadCount = unread,
    )
}
