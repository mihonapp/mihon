package mihon.reader.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderModelInvariantTest {
    @Test
    fun `page identities reject empty chapter or entry names`() {
        shouldThrow<IllegalArgumentException> { PageId(chapterId = "", entryName = "page.jpg") }
        shouldThrow<IllegalArgumentException> { PageId(chapterId = "chapter", entryName = "") }
    }

    @Test
    fun `frame identities reject negative frame indexes`() {
        shouldThrow<IllegalArgumentException> { FrameId(PageId("chapter", "page.gif"), frameIndex = -1) }
    }

    @Test
    fun `page descriptors reject nonpositive dimensions`() {
        shouldThrow<IllegalArgumentException> { PageDescriptor(PageId("chapter", "page.jpg"), width = 0, height = 10) }
        shouldThrow<IllegalArgumentException> { PageDescriptor(PageId("chapter", "page.jpg"), width = 10, height = -1) }
    }

    @Test
    fun `viewport rejects nonpositive dimensions`() {
        shouldThrow<IllegalArgumentException> { ReaderViewport(width = 0, height = 10) }
        shouldThrow<IllegalArgumentException> { ReaderViewport(width = 10, height = -1) }
    }

    @Test
    fun `reader position rejects invalid selected page indexes`() {
        shouldThrow<IllegalArgumentException> { ReaderPosition(pageIndex = -1) }
        ReaderPosition(pageIndex = 0).pageIndex shouldBe 0
    }

    @Test
    fun `reader exposes exactly the six reader modes and three scale modes`() {
        ReadingMode.entries.shouldContainExactly(
            ReadingMode.SINGLE_LTR,
            ReadingMode.SINGLE_RTL,
            ReadingMode.DUAL_LTR,
            ReadingMode.DUAL_RTL,
            ReadingMode.VERTICAL,
            ReadingMode.WEBTOON,
        )
        ScaleMode.entries.shouldContainExactly(ScaleMode.ORIGINAL, ScaleMode.FIT_WIDTH, ScaleMode.FIT_HEIGHT)
    }

    @Test
    fun `reader layout clamps zoom to the supported range`() {
        ReaderLayout.clampZoom(0.1f) shouldBe 0.25f
        ReaderLayout.clampZoom(1f) shouldBe 1f
        ReaderLayout.clampZoom(10f) shouldBe 8f
    }

    @Test
    fun `reader layout clamps pan after page layout uses viewport bounds`() {
        ReaderLayout.clampPostLayoutPan(
            viewport = ReaderViewport(width = 100, height = 100),
            contentWidth = 200,
            contentHeight = 150,
            requested = ReaderPan(x = 99f, y = -99f),
        ) shouldBe ReaderPan(x = 50f, y = -25f)

        ReaderLayout.clampPostLayoutPan(
            viewport = ReaderViewport(width = 100, height = 100),
            contentWidth = 80,
            contentHeight = 90,
            requested = ReaderPan(x = 1f, y = -1f),
        ) shouldBe ReaderPan(x = 0f, y = 0f)
    }
}
