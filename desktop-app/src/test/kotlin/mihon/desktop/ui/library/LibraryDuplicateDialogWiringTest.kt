package mihon.desktop.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import mihon.desktop.library.model.LibraryManga
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class LibraryDuplicateDialogWiringTest {
    @Test
    fun `library screen renders presenter duplicate dialog state`() = runComposeUiTest {
        val item = LibraryManga(
            id = 1L,
            sourceId = 100L,
            url = "/1",
            title = "One Piece",
            thumbnailUrl = null,
            chapterCount = 10L,
            unreadCount = 0L,
        )
        setContent {
            MaterialTheme {
                LibraryScreen(
                    state = LibraryUiState(
                        loading = false,
                        items = listOf(item),
                        duplicateDialog = DuplicateMangaDialogState(
                            target = item.toDuplicateCandidate(),
                            candidates = listOf(
                                item.copy(id = 2L, sourceId = 200L).toDuplicateCandidate(),
                            ),
                        ),
                    ),
                    onQueryChange = {},
                    onMangaSelected = {},
                    onImportBackup = {},
                    onImportLocal = {},
                )
            }
        }

        onNodeWithTag("duplicate-manga-dialog").assertExists()
        onNodeWithTag("duplicate-candidate-2").assertExists()
    }
}
