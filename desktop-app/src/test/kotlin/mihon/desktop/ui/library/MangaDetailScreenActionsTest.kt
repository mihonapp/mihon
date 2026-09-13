package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.library.model.CategoryRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaDetails
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MangaDetailScreenActionsTest {

    private fun sampleManga() = MangaDetails(
        id = 1L,
        sourceId = 100L,
        url = "/manga/one-piece",
        title = "One Piece",
        artist = "Eiichiro Oda",
        author = "Eiichiro Oda",
        description = "Pirate adventure",
        genreJson = "[\"Action\",\"Adventure\"]",
        status = 1L,
        thumbnailUrl = null,
        favorite = true,
        dateAdded = 1000L,
        viewerFlags = 0L,
        chapterFlags = 0L,
        updateStrategy = "ALWAYS_UPDATE",
        lastModifiedAt = 0L,
        favoriteModifiedAt = 0L,
        excludedScanlatorsJson = "[]",
        version = 0L,
        notes = "Notes",
        initialized = true,
        memoJson = "{}",
        categories = listOf(CategoryRecord(1L, "Favorites")),
    )

    private fun chapter(
        id: Long,
        name: String,
        read: Boolean = false,
        bookmark: Boolean = false,
        number: Double = 1.0,
    ) =
        LibraryChapter(
            id = id,
            mangaId = 1L,
            url = "/chapter/$id",
            name = name,
            scanlator = null,
            read = read,
            bookmark = bookmark,
            lastPageRead = if (read) 20L else 0L,
            dateFetch = 1000L,
            dateUpload = 1000L,
            chapterNumber = number,
            sourceOrder = id,
            lastModifiedAt = 0L,
            version = 0L,
            memoJson = "{}",
        )

    @Test
    fun `extended fab displays resume reading and triggers read callback`() = runComposeUiTest {
        var readChapterId: Long? = null

        val state = MangaDetailUiState(
            loading = false,
            manga = sampleManga(),
            chapters = listOf(
                chapter(201L, "Chapter 1 - Romance Dawn", read = true, number = 1.0),
                chapter(202L, "Chapter 2 - They Call Him Straw Hat", read = false, number = 2.0),
                chapter(203L, "Chapter 3 - Morgan vs Luffy", read = false, number = 3.0),
            ),
            readerAvailability = mapOf(
                201L to ChapterReaderAvailability.Readable,
                202L to ChapterReaderAvailability.Readable,
                203L to ChapterReaderAvailability.Readable,
            ),
        )

        setContent {
            MaterialTheme {
                MangaDetailScreen(
                    state = state,
                    onBack = {},
                    onReadChapter = { readChapterId = it },
                )
            }
        }

        // FAB should be displayed with resume text (chapter 2 is the next unread chapter)
        onNodeWithTag("manga-detail-fab-read").assertIsDisplayed()
        onNodeWithTag("manga-detail-fab-read").performClick()

        readChapterId shouldBe 202L
    }

    @Test
    fun `chapter search toggle and filter works as expected`() = runComposeUiTest {
        val state = MangaDetailUiState(
            loading = false,
            manga = sampleManga(),
            chapters = listOf(
                chapter(201L, "Chapter 1 - Romance Dawn", read = true, number = 1.0),
                chapter(202L, "Chapter 2 - They Call Him Straw Hat", read = false, number = 2.0),
                chapter(203L, "Chapter 3 - Morgan vs Luffy", read = false, number = 3.0),
            ),
            readerAvailability = mapOf(
                201L to ChapterReaderAvailability.Readable,
                202L to ChapterReaderAvailability.Readable,
                203L to ChapterReaderAvailability.Readable,
            ),
        )

        setContent {
            MaterialTheme {
                MangaDetailScreen(
                    state = state,
                    onBack = {},
                    onReadChapter = {},
                )
            }
        }

        // Toggle search box
        onNodeWithTag("chapter-search-toggle-button").assertIsDisplayed()
        onNodeWithTag("chapter-search-toggle-button").performClick()

        // Filter chapters
        onNodeWithTag("chapter-search-text-field").assertIsDisplayed()
        onNodeWithTag("chapter-search-text-field").performTextInput("Morgan")

        // Only chapter 3 matches
        onNodeWithText("Chapter 3 - Morgan vs Luffy").assertIsDisplayed()
        onNodeWithText("Chapter 1 - Romance Dawn").assertDoesNotExist()
        onNodeWithText("Chapter 2 - They Call Him Straw Hat").assertDoesNotExist()
    }

    @Test
    fun `chapter multi-selection mode and batch action menu work correctly`() = runComposeUiTest {
        var batchReadIds: Set<Long>? = null
        var batchBookmarkIds: Set<Long>? = null

        val state = MangaDetailUiState(
            loading = false,
            manga = sampleManga().copy(description = "", notes = "", categories = emptyList(), genreJson = "[]"),
            chapters = listOf(
                chapter(201L, "Chapter 1 - Romance Dawn", read = false, number = 1.0),
                chapter(202L, "Chapter 2 - They Call Him Straw Hat", read = false, number = 2.0),
                chapter(203L, "Chapter 3 - Morgan vs Luffy", read = false, number = 3.0),
            ),
            readerAvailability = mapOf(
                201L to ChapterReaderAvailability.Readable,
                202L to ChapterReaderAvailability.Readable,
                203L to ChapterReaderAvailability.Readable,
            ),
        )

        setContent {
            MaterialTheme {
                MangaDetailScreen(
                    state = state,
                    onBack = {},
                    onReadChapter = {},
                    onBatchMarkChaptersRead = { ids, _ -> batchReadIds = ids },
                    onBatchBookmarkChapters = { ids, _ -> batchBookmarkIds = ids },
                )
            }
        }

        // Toggle selection mode
        onNodeWithTag("chapter-selection-toggle-button").assertIsDisplayed()
        onNodeWithTag("chapter-selection-toggle-button").performClick()

        // Selection checkboxes are displayed
        onNodeWithTag("chapter-select-201", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("chapter-select-202", useUnmergedTree = true).assertIsDisplayed()

        // Click on item 201 to select
        onNodeWithTag("chapter-select-201", useUnmergedTree = true).performClick()

        // Batch action menu is displayed
        onNodeWithTag("manga-bottom-action-menu").assertIsDisplayed()
        onNodeWithTag("batch-chapter-selected-count").assertIsDisplayed()

        // Test select all
        onNodeWithTag("batch-chapter-select-all").performClick()

        // Click batch mark as read
        onNodeWithTag("batch-chapter-mark-read").performClick()
        batchReadIds shouldBe setOf(201L, 202L, 203L)

        // Click batch bookmark
        onNodeWithTag("batch-chapter-bookmark").performClick()
        batchBookmarkIds shouldBe setOf(201L, 202L, 203L)

        // Close selection mode
        onNodeWithTag("batch-chapter-close").performClick()
        onNodeWithTag("manga-bottom-action-menu").assertDoesNotExist()
    }

    @Test
    fun `cover click opens MangaCoverDialog with save and change options`() = runComposeUiTest {
        val state = MangaDetailUiState(
            loading = false,
            manga = sampleManga(),
            chapters = emptyList(),
        )

        setContent {
            MaterialTheme {
                MangaDetailScreen(
                    state = state,
                    onBack = {},
                )
            }
        }

        onNodeWithTag("manga-detail-cover-clickable").assertIsDisplayed()
        onNodeWithTag("manga-detail-cover-clickable").performClick()

        // Cover dialog should appear
        onNodeWithTag("manga-cover-dialog-card").assertIsDisplayed()
        onNodeWithTag("manga-cover-dialog-image", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("manga-cover-dialog-save-btn").assertIsDisplayed()
        onNodeWithTag("manga-cover-dialog-change-btn").assertIsDisplayed()

        // Dismiss dialog
        onNodeWithTag("manga-cover-dialog-close").performClick()
        onNodeWithTag("manga-cover-dialog-card").assertDoesNotExist()
    }

    @Test
    fun `notes button opens MangaNotesDialog and saves notes`() = runComposeUiTest {
        var savedNotesResult: String? = null

        val state = MangaDetailUiState(
            loading = false,
            manga = sampleManga().copy(notes = "Existing notes"),
            chapters = emptyList(),
        )

        setContent {
            MaterialTheme {
                MangaDetailScreen(
                    state = state,
                    onBack = {},
                    onSaveMangaInfo = { _, _, _, _, _, _, notes ->
                        savedNotesResult = notes
                    },
                )
            }
        }

        onNodeWithTag("manga-detail-edit-notes-btn").assertIsDisplayed()
        onNodeWithTag("manga-detail-edit-notes-btn").performClick()

        // Notes dialog is displayed
        onNodeWithTag("manga-notes-dialog").assertIsDisplayed()
        onNodeWithTag("manga-notes-input").assertIsDisplayed()
        onNodeWithTag("manga-notes-save").assertIsDisplayed()

        onNodeWithTag("manga-notes-save").performClick()
        savedNotesResult shouldBe "Existing notes"
    }
}
