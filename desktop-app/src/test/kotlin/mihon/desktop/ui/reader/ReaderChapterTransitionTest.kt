package mihon.desktop.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.reader.DesktopReaderSettings
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderChapterTransitionTest {

    @Test
    fun `target selection honors read filtered and duplicate skips`() {
        val chapters = listOf(
            chapter(1, "Ch. 1", read = true),
            chapter(2, "Ch. 2", filtered = true),
            chapter(3, "Ch. 3", duplicate = true),
            chapter(4, "Ch. 4"),
        )
        val settings = DesktopReaderSettings(
            skipReadChapters = true,
            skipFilteredChapters = true,
            skipDuplicateChapters = true,
        )

        selectChapterTransitionTarget(chapters, 0, ReaderChapterTransitionDirection.NEXT, settings) shouldBe
            chapters[3]
        selectChapterTransitionTarget(chapters, 3, ReaderChapterTransitionDirection.PREVIOUS, settings) shouldBe null
        selectChapterTransitionTarget(chapters, 0, ReaderChapterTransitionDirection.NEXT) shouldBe chapters[2]
    }

    @Test
    fun `transition model resolves previous current next and target from catalog`() {
        val chapters = listOf(
            chapter(1, "Chapter 1"),
            chapter(2, "Chapter 2"),
            chapter(3, "Chapter 3"),
        )
        val transition = readerChapterTransition(
            direction = ReaderChapterTransitionDirection.NEXT,
            current = chapters[1],
            chapters = chapters,
        )

        transition.previous shouldBe chapters[0]
        transition.current shouldBe chapters[1]
        transition.next shouldBe chapters[2]
        transition.target shouldBe chapters[2]
    }

    @Test
    fun `transition surface shows titles statuses and no-next state`() = runComposeUiTest {
        val transition = ReaderChapterTransition(
            direction = ReaderChapterTransitionDirection.NEXT,
            previous = chapter(1, "Chapter 1", downloaded = true),
            current = chapter(2, "Chapter 2", downloaded = false),
            next = null,
            target = null,
        )

        setContent {
            MaterialTheme {
                ReaderChapterTransitionSurface(
                    transition = transition,
                    onContinue = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag("reader-transition-previous-title").assertTextContains("Chapter 1")
        onNodeWithTag("reader-transition-previous-status").assertTextContains("Downloaded")
        onNodeWithTag("reader-transition-current-title").assertTextContains("Chapter 2")
        onNodeWithTag("reader-transition-current-status").assertTextContains("Not downloaded")
        onNodeWithTag("reader-transition-next-missing").assertTextContains("No next chapter")
        onNodeWithTag("reader-transition-target").assertTextContains("No next chapter available")
    }

    @Test
    fun `transition surface shows missing status and no-previous state`() = runComposeUiTest {
        val transition = ReaderChapterTransition(
            direction = ReaderChapterTransitionDirection.PREVIOUS,
            previous = null,
            current = chapter(2, "Chapter 2", available = false),
            next = null,
            target = null,
        )

        setContent {
            MaterialTheme {
                ReaderChapterTransitionSurface(
                    transition = transition,
                    onContinue = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag("reader-transition-previous-missing").assertTextContains("No previous chapter")
        onNodeWithTag("reader-transition-current-status").assertTextContains("Missing")
        onNodeWithTag("reader-transition-target").assertTextContains("No previous chapter available")
    }

    @Test
    fun `transition surface invokes continue and dismiss callbacks`() = runComposeUiTest {
        var continued = 0
        var dismissed = 0
        val transition = ReaderChapterTransition(
            direction = ReaderChapterTransitionDirection.NEXT,
            previous = null,
            current = chapter(1, "Chapter 1"),
            next = chapter(2, "Chapter 2", downloaded = true),
            target = chapter(2, "Chapter 2", downloaded = true),
        )

        setContent {
            MaterialTheme {
                ReaderChapterTransitionSurface(
                    transition = transition,
                    onContinue = { continued++ },
                    onDismiss = { dismissed++ },
                )
            }
        }

        onNodeWithTag("reader-transition-continue").performClick()
        onNodeWithTag("reader-transition-dismiss").performClick()

        continued shouldBe 1
        dismissed shouldBe 1
    }

    private fun chapter(
        id: Long,
        title: String,
        downloaded: Boolean = false,
        available: Boolean = true,
        read: Boolean = false,
        filtered: Boolean = false,
        duplicate: Boolean = false,
    ) = ReaderChapterTransitionChapter(
        id = id,
        title = title,
        downloaded = downloaded,
        available = available,
        read = read,
        filtered = filtered,
        duplicate = duplicate,
    )
}
