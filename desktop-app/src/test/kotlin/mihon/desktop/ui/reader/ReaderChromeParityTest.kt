package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import mihon.reader.model.ReadingMode
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderChromeParityTest {
    @Test
    fun `chrome follows Mihon top navigator and bottom bar hierarchy`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(1024.dp, 700.dp)) {
                    ReaderScreen(
                        session = TestReaderSession(
                            testReaderState(
                                pageCount = 12,
                                selectedIndex = 2,
                                hasPreviousChapter = true,
                                hasNextChapter = true,
                            ),
                        ),
                        title = "Manga title",
                        chapterTitle = "Chapter 4",
                        settingsStore = null,
                        onBack = {},
                        hasPreviousChapter = true,
                        hasNextChapter = true,
                    )
                }
            }
        }

        onNodeWithTag("reader-top-bar").assertIsDisplayed()
        onNodeWithTag("reader-chapter-navigator").assertIsDisplayed()
        onNodeWithTag("reader-bottom-bar").assertIsDisplayed()
        onNodeWithTag("reader-current-page").assertTextContains("3")
        onNodeWithTag("reader-total-pages").assertTextContains("12")

        onNodeWithTag("reader-fullscreen").assertDoesNotExist()
        onNodeWithTag("reader-borderless").assertDoesNotExist()
        onNodeWithTag("reader-shortcuts-btn").assertDoesNotExist()
        onNodeWithTag("reader-page-actions").assertDoesNotExist()

        onNodeWithTag("reader-overflow").performClick()
        onNodeWithTag("reader-fullscreen").assertIsDisplayed()
        onNodeWithTag("reader-borderless").assertIsDisplayed()
        onNodeWithTag("reader-shortcuts-btn").assertIsDisplayed()
        onNodeWithTag("reader-page-actions").assertIsDisplayed()
    }

    @Test
    fun `RTL mode keeps page labels stable and exposes RTL slider direction`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(900.dp, 650.dp)) {
                    ReaderScreen(
                        session = TestReaderSession(
                            testReaderState(pageCount = 20, selectedIndex = 9, mode = ReadingMode.SINGLE_RTL),
                        ),
                        title = "RTL manga",
                        chapterTitle = "Chapter 9",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }

        onNodeWithTag("reader-current-page").assertTextContains("10")
        onNodeWithTag("reader-total-pages").assertTextContains("20")
        onNodeWithTag("reader-scrubber-slider-rtl").assertIsDisplayed()
    }
}
