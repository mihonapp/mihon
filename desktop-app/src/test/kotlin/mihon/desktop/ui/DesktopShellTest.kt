package mihon.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.library.LibraryUiState
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopShellTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Library destination renders the real library screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    libraryState = LibraryUiState(loading = false),
                )
            }
        }

        onNodeWithTag("library-screen").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `selected headline has positive bounds beside the navigation rail`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Settings,
                    onDestinationSelected = {},
                )
            }
        }

        val headlineBounds = onNodeWithTag(DESKTOP_MAIN_HEADLINE_TEST_TAG).getBoundsInRoot()
        val railBounds = onNodeWithTag(DESKTOP_NAVIGATION_RAIL_TEST_TAG).getBoundsInRoot()

        assertTrue(headlineBounds.width > 0.dp, "Selected headline must have positive width")
        assertTrue(
            headlineBounds.left >= railBounds.right,
            "Selected headline must be laid out beside the navigation rail",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Downloads destination renders downloads screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Downloads,
                    onDestinationSelected = {},
                )
            }
        }

        onNodeWithTag(mihon.desktop.ui.tasks.DOWNLOADS_SCREEN_TEST_TAG).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Updates destination renders updates screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Updates,
                    onDestinationSelected = {},
                )
            }
        }

        onNodeWithTag(mihon.desktop.ui.updates.UPDATES_SCREEN_TEST_TAG).assertExists()
    }
}
