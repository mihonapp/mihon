package mihon.desktop.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.shouldBe
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderAction
import mihon.reader.session.ReaderState
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ReaderLayoutTest {

    @Test
    fun `paged spread uses core grouping and reverses only visual RTL placement`() {
        ready(ReadingMode.DUAL_LTR).visibleSpread().pageIndices.shouldContainExactly(0, 1)
        ready(ReadingMode.DUAL_RTL).visibleSpread().pageIndices.shouldContainExactly(1, 0)
        ready(ReadingMode.DUAL_LTR, coverOffset = true).visibleSpread().pageIndices.shouldContainExactly(0)
        ready(ReadingMode.DUAL_RTL, coverOffset = true, selected = 1)
            .visibleSpread().pageIndices.shouldContainExactly(2, 1)
    }

    @Test
    fun `scale zoom and post-layout pan stay within core bounds`() {
        val transform = calculatePageTransform(
            page = page(0, width = 2000, height = 1600),
            viewport = ReaderViewport(1000, 800),
            scaleMode = ScaleMode.FIT_WIDTH,
            zoom = 2f,
            requestedPan = ReaderPan(900f, -900f),
        )

        transform.widthPixels shouldBe 2000
        transform.heightPixels shouldBe 1600
        transform.zoom.shouldBeExactly(2f)
        transform.pan shouldBe ReaderPan(500f, -400f)
    }

    @Test
    fun `dual LTR and RTL place stable page identities on opposite sides`() = runComposeUiTest {
        setReaderContent(1280, 800, ready(ReadingMode.DUAL_LTR))
        val ltrZero = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        val ltrOne = onNodeWithTag(pageTag(1)).getBoundsInRoot()
        (ltrZero.left < ltrOne.left) shouldBe true

        setReaderContent(1280, 800, ready(ReadingMode.DUAL_RTL))
        val rtlZero = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        val rtlOne = onNodeWithTag(pageTag(1)).getBoundsInRoot()
        (rtlOne.left < rtlZero.left) shouldBe true
    }

    @Test
    fun `dual cover offset renders a centered half-width cover`() = runComposeUiTest {
        setReaderContent(1280, 800, ready(ReadingMode.DUAL_LTR, coverOffset = true))

        val canvas = onNodeWithTag("reader-canvas").getBoundsInRoot()
        val cover = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        cover.width shouldBe 640.dp
        cover.left shouldBe canvas.left + 320.dp
        onNodeWithTag(pageTag(1)).assertDoesNotExist()
    }

    @Test
    fun `vertical is gapped and webtoon is zero-gap fit-width`() = runComposeUiTest {
        setReaderContent(800, 1000, ready(ReadingMode.VERTICAL, pageWidth = 800, pageHeight = 400))
        val verticalFirst = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        val verticalSecond = onNodeWithTag(pageTag(1)).getBoundsInRoot()
        (verticalSecond.top - verticalFirst.bottom) shouldBe 16.dp

        setReaderContent(800, 1000, ready(ReadingMode.WEBTOON, pageWidth = 800, pageHeight = 400))
        val webtoonFirst = onNodeWithTag(pageTag(0)).getBoundsInRoot()
        val webtoonSecond = onNodeWithTag(pageTag(1)).getBoundsInRoot()
        (webtoonSecond.top - webtoonFirst.bottom) shouldBe 0.dp
        onNodeWithTag(pageTag(0)).assert(SemanticsMatcher.expectValue(ReaderScaleModeKey, ScaleMode.FIT_WIDTH))
    }

    @Test
    fun `continuous reader starts at core anchor and never composes the whole chapter`() = runComposeUiTest {
        val actions = mutableListOf<ReaderAction>()
        val state = ready(
            mode = ReadingMode.VERTICAL,
            selected = 5,
            pageCount = 50,
            pageWidth = 800,
            pageHeight = 600,
        ).copy(viewportAnchor = ReaderPosition(5, 20))

        setReaderContent(800, 1000, state, actions::add)
        waitForIdle()

        onNodeWithTag(pageTag(5)).assertExists()
        onNodeWithTag(pageTag(49)).assertDoesNotExist()
        actions.filterIsInstance<ReaderAction.SetViewportAnchor>().last().position shouldBe ReaderPosition(5, 20)
    }

    @Test
    fun `page semantics expose the applied smooth zoom transform`() = runComposeUiTest {
        setReaderContent(
            1280,
            800,
            ready(ReadingMode.SINGLE_LTR).copy(zoom = 2f, pan = ReaderPan(200f, 100f)),
        )

        onNodeWithTag(pageTag(0))
            .assert(SemanticsMatcher.expectValue(ReaderZoomKey, 2f))
            .assert(SemanticsMatcher.expectValue(ReaderPanXKey, 200f))
            .assert(SemanticsMatcher.expectValue(ReaderPanYKey, 100f))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setReaderContent(
        width: Int,
        height: Int,
        state: ReaderState,
        onAction: (ReaderAction) -> Unit = {},
    ) {
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                ReaderCanvas(state = state, onAction = onAction)
            }
        }
    }

    private fun ready(
        mode: ReadingMode,
        coverOffset: Boolean = false,
        selected: Int = 0,
        pageCount: Int = 8,
        pageWidth: Int = 600,
        pageHeight: Int = 900,
    ): ReaderState = ReaderState.ready(
        chapterId = 7,
        pages = List(pageCount) { page(it, pageWidth, pageHeight) },
        selectedIndex = selected,
    ).copy(
        mode = mode,
        coverOffset = coverOffset,
        scaleMode = ScaleMode.FIT_WIDTH,
        visiblePages = listOf(page(selected, pageWidth, pageHeight).id),
    )

    private fun page(index: Int, width: Int = 600, height: Int = 900) =
        PageDescriptor(PageId("chapter", "page-$index.png"), width, height)
}
