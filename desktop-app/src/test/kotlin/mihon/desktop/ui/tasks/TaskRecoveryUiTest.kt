package mihon.desktop.ui.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import mihon.desktop.library.update.LibraryUpdateProgress
import mihon.desktop.ui.updates.UPDATES_CHECK_NOW_BUTTON_TEST_TAG
import mihon.desktop.ui.updates.UpdatesScreen
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class TaskRecoveryUiTest {
    @Test
    fun `running update remains cancellable while current source is visible`() = runComposeUiTest {
        var cancelled = false
        setContent {
            MaterialTheme {
                UpdatesScreen(
                    emptyList(),
                    true,
                    null,
                    {},
                    {},
                    progress = LibraryUpdateProgress("Sample manga", 3, 10, 7),
                    sourceNameFor = { "Sample source" },
                    onCancelUpdate = { cancelled = true },
                )
            }
        }
        onNodeWithText("3/10 · Sample source · Sample manga").assertExists()
        onNodeWithTag(UPDATES_CHECK_NOW_BUTTON_TEST_TAG).performClick()
        assertTrue(cancelled)
    }

    @Test
    fun `empty recovered queue still explains recovery and storage failure`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DownloadsScreen(
                    emptyList(), false, 0.0, {}, {}, {}, {}, {},
                    onReadChapter = { _, _ -> },
                    recoveryMessage = "Recovered from backup", storageError = "Download disk unavailable",
                )
            }
        }
        onNodeWithText("Recovered from backup").assertExists()
        onNodeWithText("Download disk unavailable").assertExists()
    }
}
