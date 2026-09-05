package mihon.desktop.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import mihon.desktop.history.DesktopHistoryGroup
import mihon.desktop.history.HistoryGrouper
import mihon.desktop.library.model.HistoryWithDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `history screen displays groups and invokes resume and delete`() = runComposeUiTest {
        var resumedChapterId: Long? = null
        var deletedChapterId: Long? = null
        var cleared = false

        val sampleItem = HistoryWithDetails(
            chapterId = 101L,
            mangaId = 1L,
            mangaTitle = "Chainsaw Man",
            mangaThumbnailUrl = null,
            mangaSourceId = 1L,
            chapterName = "Chapter 1",
            chapterNumber = 1.0,
            lastPageRead = 5L,
            read = false,
            lastRead = System.currentTimeMillis(),
            readDuration = 120000L,
        )

        val groups = HistoryGrouper.group(listOf(sampleItem))

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                HistoryScreen(
                    groups = groups,
                    query = "",
                    onQueryChange = {},
                    onReadChapter = { resumedChapterId = it },
                    onDeleteItem = { deletedChapterId = it },
                    onClearAll = { cleared = true },
                )
            }
        }

        onNodeWithTag("history-screen").assertExists()
        onNodeWithTag("history-group-Today").assertExists()
        onNodeWithText("Chainsaw Man").assertExists()

        // Resume
        onNodeWithTag("history-resume-101").performClick()
        assertEquals(101L, resumedChapterId)

        // Delete
        onNodeWithTag("history-delete-101").performClick()
        assertEquals(101L, deletedChapterId)

        // Clear all button & confirmation dialog
        onNodeWithTag("history-clear-all-button").performClick()
        onNodeWithTag("history-confirm-clear-button").performClick()
        assertTrue(cleared)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `history screen empty state displays when no items`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                HistoryScreen(
                    groups = emptyList(),
                    query = "",
                    onQueryChange = {},
                    onReadChapter = {},
                    onDeleteItem = {},
                    onClearAll = {},
                )
            }
        }

        onNodeWithTag("history-empty-state").assertExists()
        onNodeWithText("No reading history recorded yet").assertExists()
    }
}
