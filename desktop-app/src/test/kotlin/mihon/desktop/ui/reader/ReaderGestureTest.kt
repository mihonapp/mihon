package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderSession
import mihon.reader.session.ReaderState
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderGestureTest {

    @Test
    fun `pan bounds follow transformed content and clamp requested pan`() {
        val bounds = ReaderGesturePolicy.panBounds(
            page = page(width = 1000, height = 2000),
            viewport = ReaderViewport(500, 500),
            scaleMode = ScaleMode.ORIGINAL,
            zoom = 2f,
        )

        bounds.maxX shouldBe 750f
        bounds.maxY shouldBe 1750f
        bounds.clamp(ReaderPan(5000f, -5000f)) shouldBe ReaderPan(750f, -1750f)
        bounds.panBy(ReaderPan(700f, -1700f), ReaderPan(200f, -200f)) shouldBe ReaderPan(750f, -1750f)
    }

    @Test
    fun `pan bounds are zero when content fits and can disable vertical pan`() {
        val fitting = ReaderGesturePolicy.panBounds(
            page = page(width = 1000, height = 2000),
            viewport = ReaderViewport(1200, 2500),
            scaleMode = ScaleMode.ORIGINAL,
            zoom = 1f,
        )
        fitting shouldBe ReaderPanBounds.ZERO

        val horizontalOnly = ReaderGesturePolicy.panBounds(
            page = page(width = 1000, height = 2000),
            viewport = ReaderViewport(500, 500),
            scaleMode = ScaleMode.ORIGINAL,
            zoom = 2f,
            allowVerticalPan = false,
        )
        horizontalOnly.maxX shouldBe 750f
        horizontalOnly.maxY shouldBe 0f
        horizontalOnly.clamp(ReaderPan(100f, 100f)) shouldBe ReaderPan(100f, 0f)
    }

    @Test
    fun `page viewport matches dual and continuous layout policies`() {
        ReaderGesturePolicy.pageViewport(
            mode = ReadingMode.DUAL_LTR,
            viewport = ReaderViewport(1280, 800),
        ) shouldBe ReaderViewport(640, 800)

        ReaderGesturePolicy.pageViewport(
            mode = ReadingMode.WEBTOON,
            viewport = ReaderViewport(1000, 800),
            webtoonMaxWidthPixels = 600,
            webtoonSidePaddingPercent = 10,
        ) shouldBe ReaderViewport(480, 800)
    }

    @Test
    fun `double tap toggles fit and configured zoom at least two times`() {
        ReaderGesturePolicy.doubleTapZoom(currentZoom = 1f) shouldBe 2f
        ReaderGesturePolicy.doubleTapZoom(currentZoom = 2.5f) shouldBe 1f
        ReaderGesturePolicy.doubleTapZoom(currentZoom = 0.5f) shouldBe 1f
        ReaderGesturePolicy.doubleTapZoom(currentZoom = 1f, configuredZoom = 3f) shouldBe 3f
        ReaderGesturePolicy.doubleTapZoom(currentZoom = 1f, configuredZoom = 1.25f) shouldBe 2f
        ReaderGesturePolicy.doubleTapZoom(currentZoom = 1f, configuredZoom = 100f) shouldBe ReaderLayout.MAX_ZOOM
    }

    @Test
    fun `scale pan preserves relative position when zoom changes`() {
        ReaderGesturePolicy.scalePan(ReaderPan(100f, -50f), fromZoom = 1f, toZoom = 2f) shouldBe
            ReaderPan(200f, -100f)
        ReaderGesturePolicy.scalePan(ReaderPan(200f, -100f), fromZoom = 2f, toZoom = 1f) shouldBe
            ReaderPan(100f, -50f)
    }

    @Test
    fun `pan resets when selected page changes but survives same page anchor updates`() {
        val state = readyState().copy(pan = ReaderPan(40f, -20f))

        state.reduce(ReaderAction.SelectPage(1)).pan shouldBe ReaderPan(0f, 0f)
        state.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(1))).pan shouldBe ReaderPan(0f, 0f)
        state.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(0, offsetPixels = 30))).pan shouldBe
            ReaderPan(40f, -20f)
    }

    @Test
    fun `gesture area emits drag double tap and pinch`() = runComposeUiTest {
        val pans = mutableListOf<ReaderPan>()
        val zoomFactors = mutableListOf<Float>()
        val doubleTaps = mutableListOf<ReaderViewport>()

        setContent {
            Box(Modifier.requiredSize(400.dp, 300.dp)) {
                ReaderGestureArea(
                    enabled = true,
                    onPress = {},
                    onTap = {},
                    onDoubleTap = { doubleTaps += it },
                    onPan = { delta, _ -> pans += delta },
                    onZoomBy = { factor, _, _ -> zoomFactors += factor },
                    modifier = Modifier.testTag("gesture-area"),
                    content = {},
                )
            }
        }
        waitForIdle()

        onNodeWithTag("gesture-area").performTouchInput {
            swipe(start = center, end = center + Offset(120f, 0f), durationMillis = 200)
        }
        waitForIdle()
        pans.isNotEmpty() shouldBe true
        (pans.sumOf { it.x.toDouble() } > 0.0) shouldBe true

        onNodeWithTag("gesture-area").performTouchInput {
            doubleClick(center)
        }
        waitForIdle()
        doubleTaps.isNotEmpty() shouldBe true

        onNodeWithTag("gesture-area").performTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f),
                end0 = center - Offset(90f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(90f, 0f),
                durationMillis = 200,
            )
        }
        waitForIdle()
        zoomFactors.isNotEmpty() shouldBe true
    }

    @Test
    fun `gesture area emits a secondary click without a primary tap`() = runComposeUiTest {
        var secondaryClicks = 0
        var primaryTaps = 0
        setContent {
            Box(Modifier.requiredSize(400.dp, 300.dp)) {
                ReaderGestureArea(
                    enabled = true,
                    onPress = {},
                    onTap = { primaryTaps++ },
                    onDoubleTap = {},
                    onPan = { _, _ -> },
                    onZoomBy = { _, _, _ -> },
                    onSecondaryClick = { secondaryClicks++ },
                    modifier = Modifier.testTag("gesture-area"),
                    content = {},
                )
            }
        }

        onNodeWithTag("gesture-area").performMouseInput { rightClick(center) }
        waitForIdle()

        secondaryClicks shouldBe 1
        primaryTaps shouldBe 0
    }

    @Test
    fun `one mouse wheel notch advances one paged image`() = runComposeUiTest {
        val session = FakeReaderSession(readyState())
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("reader-gesture-area").performMouseInput {
            moveTo(center)
            scroll(1f)
        }
        waitForIdle()

        session.actions shouldContain ReaderAction.Next
    }

    @Test
    fun `mouse wheel page navigation does not reveal hidden reader chrome`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val session = FakeReaderSession(readyState())
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("reader-next-region").performClick()
        mainClock.advanceTimeBy(2_600)
        waitForIdle()
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, false))
        val previousNextCount = session.actions.count { it == ReaderAction.Next }

        onNodeWithTag("reader-gesture-area").performMouseInput {
            moveTo(center)
            scroll(1f)
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()

        session.actions.count { it == ReaderAction.Next } shouldBe previousNextCount + 1
        onNodeWithTag("reader-chrome")
            .assert(SemanticsMatcher.expectValue(ReaderChromeVisibleKey, false))
    }

    @Test
    fun `reader screen drags pan and double tap toggles zoom`() = runComposeUiTest {
        val session = FakeReaderSession(
            readyState().copy(
                zoom = 2f,
                viewport = ReaderViewport(800, 600),
            ),
        )
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("reader-gesture-area").performTouchInput {
            swipe(start = center, end = center + Offset(120f, 0f), durationMillis = 200)
        }
        waitForIdle()

        val pans = session.actions.filterIsInstance<ReaderAction.SetPan>()
        (pans.size > 1) shouldBe true
        (pans.last().pan.x > 0f) shouldBe true
        (pans.last().pan.x <= 400f) shouldBe true

        onNodeWithTag("reader-gesture-area").performTouchInput {
            doubleClick(center)
        }
        waitForIdle()

        session.actions shouldContain ReaderAction.SetZoom(1f)
        session.actions.filterIsInstance<ReaderAction.SetPan>().last() shouldBe ReaderAction.SetPan(
            ReaderPan(0f, 0f),
        )
    }

    @Test
    fun `continuous vertical swipe scrolls without dispatching vertical pan`() = runComposeUiTest {
        val session = FakeReaderSession(
            ReaderState.ready(
                chapterId = 7,
                pages = List(20) { index ->
                    PageDescriptor(PageId("chapter-7", "page-$index.png"), 600, 900)
                },
                selectedIndex = 0,
            ).copy(
                mode = ReadingMode.VERTICAL,
                viewport = ReaderViewport(800, 600),
            ),
        )
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(800.dp, 600.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag("reader-gesture-area").performTouchInput {
            swipe(start = center, end = center - Offset(0f, 300f), durationMillis = 300)
        }
        waitForIdle()

        val anchor = session.actions.filterIsInstance<ReaderAction.SetViewportAnchor>().last()
        (anchor.position.pageIndex > 0 || anchor.position.offsetPixels > 0) shouldBe true
        session.actions.filterIsInstance<ReaderAction.SetPan>().all { it.pan.y == 0f } shouldBe true
    }

    @Test
    fun `chapter change resets a non-zero pan`() = runComposeUiTest {
        val session = FakeReaderSession(readyState())
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(400.dp, 300.dp)) {
                    ReaderScreen(
                        session = session,
                        title = "Manga title",
                        chapterTitle = "Chapter 7",
                        settingsStore = null,
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        runOnUiThread {
            session.mutable.value = session.mutable.value.copy(
                chapterId = 8,
                pan = ReaderPan(40f, 20f),
            )
        }
        waitForIdle()

        session.actions.filterIsInstance<ReaderAction.SetPan>().last() shouldBe ReaderAction.SetPan(
            ReaderPan(0f, 0f),
        )
    }

    private fun readyState(
        chapterId: Long = 7,
        selected: Int = 0,
        pageCount: Int = 4,
    ): ReaderState = ReaderState.ready(
        chapterId = chapterId,
        pages = List(pageCount) { index ->
            PageDescriptor(PageId("chapter-$chapterId", "page-$index.png"), 600, 900)
        },
        selectedIndex = selected,
    )

    private fun page(width: Int = 600, height: Int = 900): PageDescriptor =
        PageDescriptor(PageId("chapter", "page.png"), width, height)

    private class FakeReaderSession(initial: ReaderState) : ReaderSession {
        val mutable = MutableStateFlow(initial)
        override val state: StateFlow<ReaderState> = mutable
        val actions = mutableListOf<ReaderAction>()

        override suspend fun open(chapterId: Long) = Unit

        override fun dispatch(action: ReaderAction) {
            actions += action
            mutable.value = runCatching { mutable.value.reduce(action) }.getOrDefault(mutable.value)
        }

        override suspend fun retry(pageId: PageId) = Unit

        override suspend fun flushProgress() = Unit

        override suspend fun closeAndFlush() = Unit

        override fun cancelWithoutFlush() = Unit
    }
}
