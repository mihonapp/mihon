package mihon.desktop.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.jetbrains.skia.Bitmap
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderPageActionsDialogTest {

    @Test
    fun `encoding a page action image does not close the displayed bitmap`() {
        val bitmap = Bitmap().apply { allocN32Pixels(2, 2) }
        val displayed = bitmap.asComposeImageBitmap()
        val target = ReaderPageActionTarget(testPage(0), 0)

        displayed.encodePageImage(target)?.bytes?.isNotEmpty() shouldBe true
        bitmap.isClosed shouldBe false

        bitmap.close()
    }

    @Test
    fun `dialog dispatches every callback`() = runComposeUiTest {
        var saved = 0
        var copied = 0
        var shared = 0
        var cover = 0
        var browser = 0
        var dismissed = 0

        setContent {
            MaterialTheme {
                ReaderPageActionsDialog(
                    onDismissRequest = { dismissed++ },
                    onSave = { saved++ },
                    onCopy = { copied++ },
                    onShare = { shared++ },
                    onSetAsCover = { cover++ },
                    onOpenInBrowser = { browser++ },
                    canSetAsCover = true,
                    canOpenInBrowser = true,
                )
            }
        }

        onNodeWithTag("reader-page-actions-dialog").assertIsDisplayed()
        onNodeWithTag("reader-page-action-save").performClick()
        onNodeWithTag("reader-page-action-copy").performClick()
        onNodeWithTag("reader-page-action-share").performClick()
        onNodeWithTag("reader-page-action-cover").performClick()
        onNodeWithTag("reader-page-action-browser").performClick()
        onNodeWithTag("reader-page-actions-cancel").performClick()

        saved shouldBe 1
        copied shouldBe 1
        shared shouldBe 1
        cover shouldBe 1
        browser shouldBe 1
        dismissed shouldBe 1
    }

    @Test
    fun `local pages hide browser and disabled cover stays unavailable`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ReaderPageActionsDialog(
                    onDismissRequest = {},
                    onSave = {},
                    onCopy = {},
                    onShare = {},
                    onSetAsCover = {},
                    onOpenInBrowser = {},
                    canSetAsCover = false,
                    canOpenInBrowser = false,
                )
            }
        }

        onNodeWithTag("reader-page-action-browser").assertDoesNotExist()
        onNodeWithTag("reader-page-action-cover").assertIsNotEnabled()
    }
}
