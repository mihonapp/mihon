package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderChapterDrawerTest {

    private val sampleChapters = listOf(
        ReaderChapterTransitionChapter(
            id = 101L,
            title = "Chapter 1 - The Beginning",
            chapterNumber = 1.0,
            read = true,
            downloaded = true,
        ),
        ReaderChapterTransitionChapter(
            id = 102L,
            title = "Chapter 2 - Next Step",
            chapterNumber = 2.0,
            read = false,
            downloaded = false,
        ),
        ReaderChapterTransitionChapter(
            id = 103L,
            title = "Chapter 3 - Climax",
            chapterNumber = 3.0,
            read = false,
            downloaded = false,
        ),
    )

    @Test
    fun `drawer displays chapters and highlights current chapter`() = runComposeUiTest {
        var selectedChapterId: Long? = null
        var dismissed = false

        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderChapterDrawer(
                        isOpen = true,
                        onDismissRequest = { dismissed = true },
                        title = "Sample Manga",
                        chapters = sampleChapters,
                        currentChapterId = 102L,
                        onSelectChapter = { selectedChapterId = it },
                    )
                }
            }
        }

        waitForIdle()
        onNodeWithTag("reader-drawer-title", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Sample Manga", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("reader-drawer-item-101", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("reader-drawer-item-102", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("reader-drawer-item-103", useUnmergedTree = true).assertIsDisplayed()

        // Clicking a chapter triggers callback and dismiss
        onNodeWithTag("reader-drawer-item-103", useUnmergedTree = true).performClick()
        selectedChapterId shouldBe 103L
        dismissed shouldBe true
    }

    @Test
    fun `drawer filters chapters by search query`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderChapterDrawer(
                        isOpen = true,
                        onDismissRequest = {},
                        title = "Sample Manga",
                        chapters = sampleChapters,
                        currentChapterId = 101L,
                        onSelectChapter = {},
                    )
                }
            }
        }

        waitForIdle()
        onNodeWithTag("reader-drawer-search", useUnmergedTree = true).performTextInput("Climax")
        waitForIdle()
        onNodeWithTag("reader-drawer-item-103", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("reader-drawer-item-101", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("reader-drawer-item-102", useUnmergedTree = true).assertDoesNotExist()
    }
}
