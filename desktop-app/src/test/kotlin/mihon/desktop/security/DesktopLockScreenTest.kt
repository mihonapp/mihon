package mihon.desktop.security

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopLockScreenTest {

    @TempDir
    lateinit var tempDir: Path

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `wrong pin shows an error and correct pin unlocks`() = runComposeUiTest {
        val controller = DesktopAppLockController(
            DesktopPreferenceStore(tempDir.resolve("lock-screen.properties")),
            clock = { 0L },
        )
        controller.enableWithPin("1234")
        var unlocked = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopLockScreen(
                    onUnlock = { pin ->
                        val result = controller.unlock(pin)
                        if (result == UnlockResult.Success) unlocked = true
                        result == UnlockResult.Success
                    },
                    onForgotPinConfirmed = {},
                )
            }
        }

        onNodeWithTag(DESKTOP_LOCK_PIN_FIELD_TEST_TAG).performTextInput("0000")
        onNodeWithTag(DESKTOP_LOCK_UNLOCK_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(DESKTOP_LOCK_ERROR_TEST_TAG).assertExists()
        unlocked shouldBe false

        onNodeWithTag(DESKTOP_LOCK_PIN_FIELD_TEST_TAG).performTextClearance()
        onNodeWithTag(DESKTOP_LOCK_PIN_FIELD_TEST_TAG).performTextInput("1234")
        onNodeWithTag(DESKTOP_LOCK_UNLOCK_BUTTON_TEST_TAG).performClick()
        unlocked shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `forgot pin only disables lock after explicit confirmation`() = runComposeUiTest {
        var forgotConfirmed = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopLockScreen(
                    onUnlock = { false },
                    onForgotPinConfirmed = { forgotConfirmed = true },
                )
            }
        }

        onNodeWithTag(DESKTOP_LOCK_FORGOT_PIN_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(DESKTOP_LOCK_FORGOT_CANCEL_BUTTON_TEST_TAG).performClick()
        forgotConfirmed shouldBe false

        onNodeWithTag(DESKTOP_LOCK_FORGOT_PIN_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(DESKTOP_LOCK_FORGOT_CONFIRM_BUTTON_TEST_TAG).performClick()
        forgotConfirmed shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `locked gate hides and disables destination content`() = runComposeUiTest {
        var contentClicks = 0

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopAppLockGate(
                    isLocked = true,
                    onUnlock = { false },
                    onForgotPinConfirmed = {},
                ) {
                    Button(
                        onClick = { contentClicks++ },
                        modifier = Modifier.testTag("locked-content-button"),
                    ) {
                        Text("Secret library")
                    }
                }
            }
        }

        onNodeWithTag(DESKTOP_LOCK_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag("locked-content-button").assertDoesNotExist()
        contentClicks shouldBe 0
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `unlocked gate renders destination content instead of lock screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopAppLockGate(
                    isLocked = false,
                    onUnlock = { true },
                    onForgotPinConfirmed = {},
                ) {
                    Text("Library content", modifier = Modifier.testTag("unlocked-content"))
                }
            }
        }

        onNodeWithTag("unlocked-content").assertExists()
        onNodeWithTag(DESKTOP_LOCK_SCREEN_TEST_TAG).assertDoesNotExist()
    }
}
