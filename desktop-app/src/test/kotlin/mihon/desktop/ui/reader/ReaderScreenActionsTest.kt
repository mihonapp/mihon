package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.reader.model.PageDescriptor
import mihon.reader.session.ReaderSession
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class ReaderScreenActionsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `chrome menu and long press open the page actions dialog with local actions`() = runComposeUiTest {
        val handler = RecordingPageActionHandler()
        val session = TestReaderSession(testReaderState())
        setReaderScreen(
            session = session,
            store = settingsStore(),
            pageActionHandler = handler,
        )

        onNodeWithTag("reader-page-actions").performClick()
        onNodeWithTag("reader-page-actions-dialog").assertIsDisplayed()
        onNodeWithTag("reader-page-action-save").assertIsDisplayed()
        onNodeWithTag("reader-page-action-copy").assertIsDisplayed()
        onNodeWithTag("reader-page-action-share").assertIsDisplayed()
        onNodeWithTag("reader-page-action-cover").assertIsDisplayed()
        onNodeWithTag("reader-page-action-browser").assertDoesNotExist()

        onNodeWithTag("reader-page-action-save").performClick()
        waitUntil { handler.saved.size == 1 }
        handler.saved.single().first.page shouldBe session.state.value.pages[0]
        onNodeWithTag("reader-page-actions-dialog").assertDoesNotExist()

        onNodeWithTag("reader-gesture-area").performTouchInput { longClick(center) }
        waitForIdle()
        onNodeWithTag("reader-page-actions-dialog").assertIsDisplayed()
    }

    @Test
    fun `secondary click opens page actions and escape dismisses it before leaving reader`() = runComposeUiTest {
        val session = TestReaderSession(testReaderState())
        var escapeRequests = 0
        var backRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore(),
            onBack = { backRequests++ },
            onEscape = {
                escapeRequests++
                true
            },
        )

        onNodeWithTag("reader-gesture-area").performMouseInput { rightClick(center) }
        onNodeWithTag("reader-page-actions-dialog").assertIsDisplayed()

        onNodeWithTag("reader-page-actions-cancel").performKeyInput {
            keyDown(Key.Escape)
            keyUp(Key.Escape)
        }
        waitForIdle()

        onNodeWithTag("reader-page-actions-dialog").assertDoesNotExist()
        escapeRequests shouldBe 0
        backRequests shouldBe 0
        session.closeRequests shouldBe 0
    }

    @Test
    fun `page action dialog wires copy share cover and browser callbacks`() = runComposeUiTest {
        val handler = RecordingPageActionHandler()
        val session = TestReaderSession(testReaderState())
        setReaderScreen(
            session = session,
            store = settingsStore(),
            pageActionHandler = handler,
            mangaId = 99L,
            pageUrlResolver = { page -> "https://example.com/${page.id.entryName}" },
        )

        onNodeWithTag("reader-page-actions").performClick()
        onNodeWithTag("reader-page-action-copy").performClick()
        waitUntil { handler.copied == 1 }
        onNodeWithTag("reader-page-actions").performClick()
        onNodeWithTag("reader-page-action-share").performClick()
        waitUntil { handler.shared == 1 }
        onNodeWithTag("reader-page-actions").performClick()
        onNodeWithTag("reader-page-action-cover").performClick()
        waitUntil { handler.coverTargets.size == 1 }
        onNodeWithTag("reader-page-actions").performClick()
        onNodeWithTag("reader-page-action-browser").performClick()
        waitUntil { handler.openedUrls.size == 1 }

        handler.copied shouldBe 1
        handler.shared shouldBe 1
        handler.coverTargets.single().mangaId shouldBe 99L
        handler.openedUrls.single() shouldBe "https://example.com/page-0.png"
    }

    @Test
    fun `bookmark toggle reflects state and persists through the bookmark store`() = runComposeUiTest {
        val settingsFile = tempDir.resolve("bookmark-settings.properties")
        val store = DesktopReaderSettingsStore(DesktopPreferenceStore(settingsFile))
        val session = TestReaderSession(testReaderState(chapterId = 7))

        setReaderScreen(session = session, store = store)

        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderBookmarkedKey, false))
        onNodeWithTag("reader-bookmark-toggle").performClick()
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderBookmarkedKey, true))
        store.isChapterBookmarked(7L) shouldBe true

        onNodeWithTag("reader-bookmark-toggle").performClick()
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderBookmarkedKey, false))
        store.isChapterBookmarked(7L) shouldBe false
    }

    @Test
    fun `chapter navigation shows a transition with titles and continues externally when enabled`() = runComposeUiTest {
        val session = TestReaderSession(
            testReaderState(chapterId = 2, hasPreviousChapter = true, hasNextChapter = true),
        )
        var previousRequests = 0
        var nextRequests = 0
        val catalog = listOf(
            ReaderChapterTransitionChapter(1L, "Chapter 1", downloaded = true),
            ReaderChapterTransitionChapter(2L, "Chapter 2"),
            ReaderChapterTransitionChapter(3L, "Chapter 3", available = false),
        )
        setReaderScreen(
            session = session,
            store = settingsStore().also { it.save(DesktopReaderSettings(alwaysShowChapterTransition = true)) },
            hasPreviousChapter = true,
            hasNextChapter = true,
            chapterCatalog = catalog,
            onPreviousChapter = { previousRequests++ },
            onNextChapter = { nextRequests++ },
        )

        onNodeWithTag("reader-scrubber-next-chapter").performClick()
        onNodeWithTag("reader-chapter-transition").assertIsDisplayed()
        onNodeWithTag("reader-transition-current-title").assertTextContains("Chapter 2")
        onNodeWithTag("reader-transition-next-title").assertTextContains("Chapter 3")
        onNodeWithTag("reader-transition-next-status").assertTextContains("Missing")

        onNodeWithTag("reader-transition-continue").performClick()
        waitUntil { session.closeRequests == 1 && nextRequests == 1 }
        onNodeWithTag("reader-chapter-transition").assertDoesNotExist()
    }

    @Test
    fun `boundary next without an adjacent chapter shows no-next transition`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val session = TestReaderSession(
            testReaderState(selectedIndex = 3, hasNextChapter = false),
        )
        setReaderScreen(
            session = session,
            store = settingsStore().also { it.save(DesktopReaderSettings(alwaysShowChapterTransition = true)) },
        )

        onNodeWithTag("reader-next-region").performClick()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        session.actions shouldContain mihon.reader.session.ReaderAction.Next
        onNodeWithTag("reader-chapter-transition").assertIsDisplayed()
        onNodeWithTag("reader-transition-next-missing").assertTextContains("No next chapter")
        onNodeWithTag("reader-transition-target").assertTextContains("No next chapter available")
    }

    @Test
    fun `disabled chapter transition keeps seamless navigation`() = runComposeUiTest {
        val session = TestReaderSession(
            testReaderState(hasPreviousChapter = true, hasNextChapter = true),
        )
        var nextRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore().also { it.save(DesktopReaderSettings(alwaysShowChapterTransition = false)) },
            hasPreviousChapter = true,
            hasNextChapter = true,
            onNextChapter = { nextRequests++ },
        )

        onNodeWithTag("reader-scrubber-next-chapter").performClick()
        waitUntil { session.closeRequests == 1 && nextRequests == 1 }
        onNodeWithTag("reader-chapter-transition").assertDoesNotExist()
    }

    @Test
    fun `page boundary navigation leaves chapter changes to the desktop navigator`() = runComposeUiTest {
        val session = TestReaderSession(
            testReaderState(selectedIndex = 3, hasNextChapter = true),
        )
        var nextRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore().also { it.save(DesktopReaderSettings(alwaysShowChapterTransition = false)) },
            hasNextChapter = true,
            onNextChapter = { nextRequests++ },
        )

        onNodeWithTag("reader-next-region").performClick()
        waitUntil { session.closeRequests == 1 && nextRequests == 1 }

        session.actions.contains(mihon.reader.session.ReaderAction.Next) shouldBe false
    }

    @Test
    fun `page boundary transition waits for confirmation before desktop navigation`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val session = TestReaderSession(
            testReaderState(chapterId = 2, selectedIndex = 3, hasNextChapter = true),
        )
        var nextRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore().also { it.save(DesktopReaderSettings(alwaysShowChapterTransition = true)) },
            hasNextChapter = true,
            chapterCatalog = listOf(
                ReaderChapterTransitionChapter(2L, "Chapter 2"),
                ReaderChapterTransitionChapter(3L, "Chapter 3"),
            ),
            onNextChapter = { nextRequests++ },
        )

        onNodeWithTag("reader-next-region").performClick()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag("reader-chapter-transition").assertIsDisplayed()
        session.closeRequests shouldBe 0
        nextRequests shouldBe 0
        session.actions.contains(mihon.reader.session.ReaderAction.Next) shouldBe false

        onNodeWithTag("reader-transition-continue").performClick()
        waitUntil { session.closeRequests == 1 && nextRequests == 1 }
    }

    @Test
    fun `chapter transition navigates to the selected skip target`() = runComposeUiTest {
        val session = TestReaderSession(testReaderState(chapterId = 2, hasNextChapter = true))
        var adjacentRequests = 0
        var selectedChapterId: Long? = null
        setReaderScreen(
            session = session,
            store = settingsStore().also {
                it.save(
                    DesktopReaderSettings(
                        alwaysShowChapterTransition = true,
                        skipReadChapters = true,
                    ),
                )
            },
            hasNextChapter = true,
            chapterCatalog = listOf(
                ReaderChapterTransitionChapter(2L, "Chapter 2"),
                ReaderChapterTransitionChapter(3L, "Chapter 3", read = true),
                ReaderChapterTransitionChapter(4L, "Chapter 4"),
            ),
            onNextChapter = { adjacentRequests++ },
            onChapterSelected = { selectedChapterId = it },
        )

        onNodeWithTag("reader-scrubber-next-chapter").performClick()
        onNodeWithTag("reader-transition-continue").performClick()
        waitUntil { session.closeRequests == 1 && selectedChapterId == 4L }

        adjacentRequests shouldBe 0
    }

    @Test
    fun `escape flushes the reader before leaving when window mode allows close`() = runComposeUiTest {
        val session = TestReaderSession(testReaderState())
        var backRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore(),
            onBack = { backRequests++ },
            onEscape = { true },
        )

        onNodeWithTag("reader-screen").performKeyInput {
            keyDown(Key.Escape)
            keyUp(Key.Escape)
        }
        waitUntil { session.closeRequests == 1 && backRequests == 1 }
    }

    @Test
    fun `escape keeps the reader open when it only exits fullscreen`() = runComposeUiTest {
        val session = TestReaderSession(testReaderState())
        var backRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore(),
            onBack = { backRequests++ },
            onEscape = { false },
        )

        onNodeWithTag("reader-screen").performKeyInput {
            keyDown(Key.Escape)
            keyUp(Key.Escape)
        }
        waitForIdle()

        session.closeRequests shouldBe 0
        backRequests shouldBe 0
    }

    @Test
    fun `progress flush failure does not trap the user inside the reader`() = runComposeUiTest {
        val session = TestReaderSession(testReaderState()).also {
            it.closeFailure = IllegalStateException("database unavailable")
        }
        var backRequests = 0
        setReaderScreen(
            session = session,
            store = settingsStore(),
            onBack = { backRequests++ },
        )

        onNodeWithTag("reader-back").performClick()
        waitUntil { session.closeRequests == 1 && backRequests == 1 }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setReaderScreen(
        session: ReaderSession,
        store: DesktopReaderSettingsStore,
        hasPreviousChapter: Boolean = false,
        hasNextChapter: Boolean = false,
        chapterCatalog: List<ReaderChapterTransitionChapter> = emptyList(),
        onPreviousChapter: () -> Unit = {},
        onNextChapter: () -> Unit = {},
        onChapterSelected: ((Long) -> Unit)? = null,
        pageActionHandler: ReaderPageActionHandler? = null,
        mangaId: Long? = null,
        pageUrlResolver: (PageDescriptor) -> String? = { null },
        onBack: () -> Unit = {},
        onEscape: () -> Boolean = { true },
    ) {
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.requiredSize(1024.dp, 700.dp)) {
                        ReaderScreen(
                            session = session,
                            title = "Manga title",
                            chapterTitle = "Chapter 2",
                            settingsStore = store,
                            onBack = onBack,
                            onEscape = onEscape,
                            onPreviousChapter = onPreviousChapter,
                            onNextChapter = onNextChapter,
                            onChapterSelected = onChapterSelected,
                            hasPreviousChapter = hasPreviousChapter,
                            hasNextChapter = hasNextChapter,
                            chapterCatalog = chapterCatalog,
                            pageActionHandler = pageActionHandler,
                            mangaId = mangaId,
                            pageUrlResolver = pageUrlResolver,
                        )
                    }
                }
            }
        }
    }

    private fun settingsStore(): DesktopReaderSettingsStore = DesktopReaderSettingsStore(
        DesktopPreferenceStore(tempDir.resolve("settings-${System.nanoTime()}.properties")),
    )

    private class RecordingPageActionHandler : ReaderPageActionHandler {
        val saved = mutableListOf<Pair<ReaderPageActionTarget, ReaderPageImage>>()
        val coverTargets = mutableListOf<ReaderPageActionTarget>()
        val openedUrls = mutableListOf<String>()
        var copied = 0
        var shared = 0

        override suspend fun loadImage(target: ReaderPageActionTarget): ReaderPageImage =
            ReaderPageImage(byteArrayOf(1, 2, 3), target.fileName)

        override fun saveImage(target: ReaderPageActionTarget, image: ReaderPageImage): Boolean {
            saved += target to image
            return true
        }

        override fun copyImage(image: ReaderPageImage): Boolean {
            copied++
            return true
        }

        override fun shareImage(image: ReaderPageImage): Boolean {
            shared++
            return true
        }

        override fun setAsCover(target: ReaderPageActionTarget, image: ReaderPageImage): Boolean {
            coverTargets += target
            return true
        }

        override fun openInBrowser(url: String): Boolean {
            openedUrls += url
            return true
        }
    }
}
