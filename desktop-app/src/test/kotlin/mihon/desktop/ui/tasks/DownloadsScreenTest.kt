package mihon.desktop.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.download.DesktopDownload
import mihon.desktop.download.DownloadPage
import mihon.desktop.download.DownloadStatus
import mihon.desktop.download.PageStatus
import org.junit.jupiter.api.Test

class DownloadsScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders empty state when queue is empty`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DownloadsScreen(
                    queue = emptyList(),
                    isRunning = false,
                    speedBytesPerSec = 0.0,
                    onPauseAll = {},
                    onResumeAll = {},
                    onClearCompleted = {},
                    onCancel = {},
                    onRetry = {},
                )
            }
        }

        onNodeWithText("No downloads in queue").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders download card and triggers pause and cancel actions`() = runComposeUiTest {
        var paused = false
        var cancelledId: Long? = null

        val download = DesktopDownload(
            chapterId = 555L,
            mangaId = 1L,
            sourceId = 2L,
            mangaTitle = "Chainsaw Man",
            chapterName = "Chapter 150",
            chapterUrl = "/ch150",
            status = DownloadStatus.DOWNLOADING,
            pages = listOf(
                DownloadPage(0, "p0", status = PageStatus.READY),
                DownloadPage(1, "p1", status = PageStatus.QUEUE),
            ),
            progress = 0.5f,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DownloadsScreen(
                    queue = listOf(download),
                    isRunning = true,
                    speedBytesPerSec = 1024.0 * 1024.0 * 2.5, // 2.5 MB/s
                    onPauseAll = { paused = true },
                    onResumeAll = {},
                    onClearCompleted = {},
                    onCancel = { cancelledId = it },
                    onRetry = {},
                )
            }
        }

        onNodeWithText("Chainsaw Man").assertExists()
        onNodeWithText("Chapter 150").assertExists()
        onNodeWithText("Downloading").assertExists()
        onNodeWithText("1 active items • 2.5 MB/s").assertExists()

        onNodeWithTag(DOWNLOADS_PAUSE_ALL_BUTTON_TEST_TAG).performClick()
        paused shouldBe true

        onNodeWithText("Cancel").performClick()
        cancelledId shouldBe 555L
    }
}
