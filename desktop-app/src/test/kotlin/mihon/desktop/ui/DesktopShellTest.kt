package mihon.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.upcoming.UPCOMING_BACK_BUTTON_TEST_TAG
import mihon.desktop.ui.upcoming.UPCOMING_SCREEN_TEST_TAG
import mihon.desktop.ui.upcoming.UpcomingScreen
import mihon.desktop.ui.upcoming.UpcomingUiState
import mihon.desktop.ui.updates.UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG
import mihon.desktop.ui.updates.UPDATES_SCREEN_TEST_TAG
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopShellTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `completed downloads expose a reader action while unfinished downloads do not`() = runComposeUiTest {
        var readChapterId: Long? = null
        var readMangaId: Long? = null
        val queue = mihon.desktop.download.DownloadStatus.entries.mapIndexed { index, status ->
            mihon.desktop.download.DesktopDownload(
                chapterId = index + 1L,
                mangaId = 42L,
                sourceId = 100L,
                mangaTitle = "Downloaded manga",
                chapterName = status.name,
                chapterUrl = "/$index",
                status = status,
            )
        }
        setContent {
            Box(modifier = Modifier.requiredSize(1100.dp, 1200.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Downloads,
                    onDestinationSelected = {},
                    downloadsQueue = queue,
                    onReadDownloadedChapter = { mangaId, chapterId ->
                        readMangaId = mangaId
                        readChapterId = chapterId
                    },
                )
            }
        }
        queue.filter { it.status != mihon.desktop.download.DownloadStatus.COMPLETED }.forEach { item ->
            onNodeWithTag("download_read_${item.chapterId}").assertDoesNotExist()
        }
        val completed = queue.single { it.status == mihon.desktop.download.DownloadStatus.COMPLETED }
        onNodeWithTag("download_read_${completed.chapterId}").performClick()
        readChapterId shouldBe completed.chapterId
        readMangaId shouldBe completed.mangaId
    }

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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Stats destination renders stats screen`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Stats,
                    onDestinationSelected = {},
                    statsData = mihon.desktop.stats.DesktopStatsData(isLoading = false),
                )
            }
        }

        onNodeWithTag(mihon.desktop.ui.stats.STATS_SCREEN_TEST_TAG).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Incognito mode shows banner in DesktopShell`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    incognitoMode = true,
                )
            }
        }

        onNodeWithTag("incognito-banner").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `lock now button in the shell invokes the app lock callback`() = runComposeUiTest {
        var lockRequested = false

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 700.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                    onLockNow = { lockRequested = true },
                )
            }
        }

        onNodeWithTag(DESKTOP_LOCK_NOW_BUTTON_TEST_TAG).performClick()
        assertTrue(lockRequested)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `lock now button is hidden when app lock is not configured`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1280.dp, 800.dp)) {
                DesktopShell(
                    selected = DesktopDestination.Library,
                    onDestinationSelected = {},
                )
            }
        }

        onNodeWithTag(DESKTOP_LOCK_NOW_BUTTON_TEST_TAG).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `upcoming opened from updates restores updates when back is pressed`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1100.dp, 760.dp)) {
                var isUpcomingOpen by remember { mutableStateOf(false) }
                DesktopShell(
                    selected = DesktopDestination.Updates,
                    onDestinationSelected = {},
                    isUpcomingOpen = isUpcomingOpen,
                    onOpenUpcoming = { isUpcomingOpen = true },
                    onCloseUpcoming = { isUpcomingOpen = false },
                    upcomingContent = if (isUpcomingOpen) {
                        {
                            UpcomingScreen(
                                state = UpcomingUiState(loading = false),
                                onBack = { isUpcomingOpen = false },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag(UPDATES_OPEN_UPCOMING_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(UPCOMING_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertDoesNotExist()

        onNodeWithTag(UPCOMING_BACK_BUTTON_TEST_TAG).performClick()
        onNodeWithTag(UPDATES_SCREEN_TEST_TAG).assertExists()
        onNodeWithTag(UPCOMING_SCREEN_TEST_TAG).assertDoesNotExist()
    }
}
