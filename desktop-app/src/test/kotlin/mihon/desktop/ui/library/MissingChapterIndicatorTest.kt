package mihon.desktop.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MissingChapterIndicatorTest {
    @Test
    fun `detail list renders a gap indicator between chapter numbers`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MangaDetailScreen(
                    state = MangaDetailUiState(
                        manga = mangaDetails(),
                        chapters = listOf(
                            chapter(1, "Chapter 1", number = 1.0, order = 0),
                            chapter(2, "Chapter 3", number = 3.0, order = 1),
                        ),
                        chapterSettings = ChapterSettings(
                            displayMode = ChapterDisplayMode.Name,
                            showMissingChapters = true,
                        ),
                        loading = false,
                    ),
                    onBack = {},
                )
            }
        }

        onNodeWithTag("missing-chapter-indicator").assertExists()
        onNodeWithText("1 missing chapter").assertExists()
    }

    private fun mangaDetails() = MangaDetails(
        id = 1L,
        sourceId = 100L,
        url = "/1",
        title = "One Piece",
        artist = null,
        author = "Author",
        description = null,
        genreJson = "[]",
        status = 0L,
        thumbnailUrl = null,
        favorite = true,
        dateAdded = 0L,
        viewerFlags = 0L,
        chapterFlags = 0L,
        updateStrategy = "ALWAYS_UPDATE",
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        excludedScanlatorsJson = "[]",
        version = 0L,
        notes = "",
        initialized = true,
        memoJson = "{}",
        categories = emptyList(),
    )

    private fun chapter(id: Long, name: String, number: Double, order: Long) = LibraryChapter(
        id = id,
        mangaId = 1L,
        url = "/$id",
        name = name,
        scanlator = null,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        dateUpload = 0L,
        chapterNumber = number,
        sourceOrder = order,
        lastModifiedAt = 0L,
        version = 0L,
        memoJson = "{}",
    )
}
