package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.reader.DesktopReaderSettings
import mihon.desktop.reader.DesktopReaderSettingsStore
import mihon.desktop.reader.ReaderColorFilter
import mihon.reader.image.IntRect
import mihon.reader.image.TileKey
import mihon.reader.model.FrameId
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderLoadState
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderSessionError
import mihon.reader.session.ReaderState
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class ReaderScreenTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ready screen exposes complete chrome and saves mode scale cover and zoom immediately`() = runComposeUiTest {
        val session = FakeReaderSession(ready())
        val store = settingsStore()
        var fullscreen = 0
        var borderless = 0
        setReaderScreen(
            session = session,
            store = store,
            onFullscreen = { fullscreen++ },
            onBorderless = { borderless++ },
        )

        onNodeWithTag("reader-back").assertIsDisplayed()
        onNodeWithTag("reader-title").assertTextContains("Manga title")
        onNodeWithTag("reader-chapter").assertTextContains("Chapter 7")
        onNodeWithTag("reader-page-counter").assertTextContains("1 / 4")
        onNodeWithTag("reader-mode-menu").performClick()
        onNodeWithTag("reader-mode-DUAL_RTL").performClick()
        onNodeWithTag("reader-cover-toggle").performClick()
        onNodeWithTag("reader-scale-menu").performClick()
        onNodeWithTag("reader-scale-FIT_HEIGHT").performClick()
        onNodeWithTag("reader-filter-menu").performClick()
        onNodeWithTag("reader-filter-INVERT").performClick()
        onNodeWithTag("reader-crop-toggle").performClick()
        onNodeWithTag("reader-zoom-in").performClick()
        onNodeWithTag("reader-fullscreen").performClick()
        onNodeWithTag("reader-borderless").performClick()

        session.actions shouldContain ReaderAction.ChangeMode(ReadingMode.DUAL_RTL)
        session.actions shouldContain ReaderAction.SetCoverOffset(true)
        session.actions shouldContain ReaderAction.SetScaleMode(ScaleMode.FIT_HEIGHT)
        session.actions shouldContain ReaderAction.SetZoom(1.25f)
        store.load().mode shouldBe ReadingMode.DUAL_RTL
        store.load().coverOffset shouldBe true
        store.load().scaleMode shouldBe ScaleMode.FIT_HEIGHT
        store.load().colorFilter shouldBe ReaderColorFilter.INVERT
        store.load().cropBorders shouldBe true
        fullscreen shouldBe 1
        borderless shouldBe 1
    }

    @Test
    fun `settings exposes constrained regions and reset restores fixed defaults before save`() = runComposeUiTest {
        val session = FakeReaderSession(ready().copy(mode = ReadingMode.DUAL_LTR, coverOffset = true))
        val store = settingsStore().also {
            it.save(DesktopReaderSettings(mode = ReadingMode.DUAL_LTR, coverOffset = true))
        }
        setReaderScreen(session, store, width = 800, height = 700)

        onNodeWithTag("reader-settings").performClick()
        onNodeWithTag("reader-setting-left-action").assertExists()
        onNodeWithTag("reader-setting-center-action").assertExists()
        onNodeWithTag("reader-setting-right-action").assertExists()
        onNodeWithTag("reader-setting-wheel").assertExists()
        onNodeWithTag("reader-setting-mode").assertExists()
        onNodeWithTag("reader-setting-scale").assertExists()
        onNodeWithTag("reader-setting-cover").assertExists()
        onNodeWithTag("reader-setting-filter").assertExists()
        onNodeWithTag("reader-setting-bg").assertExists()
        onNodeWithTag("reader-setting-crop-paged").assertExists()
        onNodeWithTag("reader-setting-crop-webtoon").assertExists()
        onNodeWithTag("reader-setting-webtoon-max-width").assertExists()
        onNodeWithTag("reader-setting-webtoon-side-padding").assertExists()
        onNodeWithTag("reader-setting-left-boundary")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        onNodeWithTag("reader-setting-center-boundary")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        onNodeWithTag("reader-settings-reset").performClick()
        onNodeWithTag("reader-settings-save").performClick()

        store.load() shouldBe DesktopReaderSettings()
        session.actions shouldContain ReaderAction.ChangeMode(ReadingMode.SINGLE_LTR)
        session.actions shouldContain ReaderAction.SetCoverOffset(false)
        session.actions shouldContain ReaderAction.SetScaleMode(ScaleMode.FIT_WIDTH)
    }

    @Test
    fun `reading input hides chrome at 2500ms while semantics remain and center reveals it`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val session = FakeReaderSession(ready())
        setReaderScreen(session, settingsStore())

        onNodeWithTag("reader-next-region").performClick()
        mainClock.advanceTimeBy(2_600)
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, false))
        onNodeWithTag("reader-back")
            .assertExists()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus))

        onNodeWithTag("reader-center-region").performClick()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, true))
        session.actions shouldContain ReaderAction.Next
    }

    @Test
    fun `loading and typed failures are visible retryable and redact raw paths`() = runComposeUiTest {
        val session = FakeReaderSession(ReaderState(loadState = ReaderLoadState.Loading(1)))
        setReaderScreen(session, settingsStore())
        onNodeWithTag("reader-loading").assertTextContains("Loading", substring = true)

        session.mutable.value = ready().copy(
            loadState = ReaderLoadState.Failed(
                ReaderSessionError(
                    ReaderErrorCode.SOURCE_UNAVAILABLE,
                    ReaderFailure.UnsupportedFormat("C:\\secret\\chapter.exe"),
                ),
            ),
            error = ReaderSessionError(
                ReaderErrorCode.SOURCE_UNAVAILABLE,
                ReaderFailure.UnsupportedFormat("C:\\secret\\chapter.exe"),
            ),
        )
        waitForIdle()
        onNodeWithTag("reader-error").assertTextContains("not supported", substring = true)
        onNodeWithText("C:\\secret\\chapter.exe", substring = true).assertDoesNotExist()
        onNodeWithTag("reader-retry").performClick()
        waitForIdle()
        session.retryRequests shouldBe 1
    }

    @Test
    fun `cache diagnostic appears only for explicit debug mode`() = runComposeUiTest {
        setReaderScreen(FakeReaderSession(ready()), settingsStore(), debugEnabled = false)
        onNodeWithTag("reader-cache-diagnostic").assertDoesNotExist()

        setReaderScreen(FakeReaderSession(ready()), settingsStore(), debugEnabled = true)
        onNodeWithTag("reader-cache-diagnostic").assertExists()
    }

    @Test
    fun `back flushes an active reader exactly once`() = runComposeUiTest {
        val session = FakeReaderSession(ready())
        var returned = 0
        setReaderScreen(session, settingsStore(), onBack = { returned++ })

        onNodeWithTag("reader-back").performClick()
        onNodeWithTag("reader-back").performClick()

        waitUntil { session.closeRequests == 1 && returned == 1 }
    }

    @Test
    fun `animated page follows selected core frame pauses offscreen and owns no timer`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val page = PageId("chapter", "animated.gif")
        var selected by mutableStateOf(FrameId(page, 0))
        var visible by mutableStateOf(true)
        var foreground by mutableStateOf(true)
        val loaded = mutableListOf<Int>()
        val visibility = mutableListOf<Boolean>()
        val backgrounds = mutableListOf<Boolean>()
        val bridge = ComposeTileBridge()
        val reporter = object : AnimationVisibilityReporter {
            override fun setContentVisible(visible: Boolean) {
                visibility += visible
            }

            override fun setForeground(foreground: Boolean) {
                backgrounds += foreground
            }
        }
        setContent {
            MaterialTheme {
                AnimatedPage(
                    selectedFrame = selected,
                    loadFrame = { frame ->
                        loaded += frame.frameIndex
                        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
                            setRGB(0, 0, if (frame.frameIndex == 0) Color.RED.rgb else Color.BLUE.rgb)
                        }
                        ReaderAnimatedFrame(
                            TileKey(page, frame, IntRect(0, 0, 1, 1)),
                            image,
                        )
                    },
                    bridge = bridge,
                    contentVisible = visible,
                    foreground = foreground,
                    visibilityReporter = reporter,
                )
            }
        }
        waitForIdle()
        onNodeWithTag("reader-animated-page")
            .assert(SemanticsMatcher.expectValue(ReaderFrameIndexKey, 0))

        mainClock.advanceTimeBy(10_000)
        loaded shouldBe listOf(0)
        mainClock.autoAdvance = true
        runOnUiThread { selected = FrameId(page, 1) }
        waitUntil { bridge.metrics.conversionCount >= 2L }
        waitForIdle()
        onNodeWithTag("reader-animated-page")
            .assert(SemanticsMatcher.expectValue(ReaderFrameIndexKey, 1))
        runOnUiThread {
            visible = false
            foreground = false
        }
        waitForIdle()
        visibility.last() shouldBe false
        backgrounds.last() shouldBe false

        setContent { Box {} }
        waitForIdle()
        bridge.metrics.retainedBytes shouldBe 0L
        bridge.close()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setReaderScreen(
        session: ReaderSession,
        store: DesktopReaderSettingsStore,
        width: Int = 1024,
        height: Int = 700,
        onFullscreen: () -> Unit = {},
        onBorderless: () -> Unit = {},
        onBack: () -> Unit = {},
        debugEnabled: Boolean = false,
    ) {
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        ReaderScreen(
                            session = session,
                            title = "Manga title",
                            chapterTitle = "Chapter 7",
                            settingsStore = store,
                            onBack = onBack,
                            onFullscreen = onFullscreen,
                            onBorderless = onBorderless,
                            debugEnabled = debugEnabled,
                        )
                    }
                }
            }
        }
    }

    private fun settingsStore() = DesktopReaderSettingsStore(
        DesktopPreferenceStore(tempDir.resolve("settings-${System.nanoTime()}.properties")),
    )

    private fun ready(): ReaderState = ReaderState.ready(
        chapterId = 7,
        pages = List(4) { index ->
            PageDescriptor(PageId("chapter", "page-$index.png"), 600, 900)
        },
        selectedIndex = 0,
    )

    private class FakeReaderSession(initial: ReaderState) : ReaderSession {
        val mutable = MutableStateFlow(initial)
        override val state: StateFlow<ReaderState> = mutable
        val actions = mutableListOf<ReaderAction>()
        var retryRequests = 0
        var closeRequests = 0

        override suspend fun open(chapterId: Long) = Unit

        override fun dispatch(action: ReaderAction) {
            actions += action
            mutable.value = runCatching { mutable.value.reduce(action) }.getOrDefault(mutable.value)
        }

        override suspend fun retry(pageId: PageId) {
            retryRequests++
        }

        override suspend fun flushProgress() = Unit

        override suspend fun closeAndFlush() {
            closeRequests++
        }

        override fun cancelWithoutFlush() = Unit
    }
}
