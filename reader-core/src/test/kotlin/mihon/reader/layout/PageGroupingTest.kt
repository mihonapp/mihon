package mihon.reader.layout

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderLayout
import mihon.reader.model.ReaderLayoutPolicy
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import org.junit.jupiter.api.Test

class PageGroupingTest {
    @Test
    fun `dual grouping pairs pages from the first page when no cover is reserved`() {
        PageGrouping.dual(pageDescriptors(5), reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(0), pageId(1)),
                listOf(pageId(2), pageId(3)),
                listOf(pageId(4)),
            )
    }

    @Test
    fun `dual grouping keeps the cover alone before pairing remaining pages`() {
        PageGrouping.dual(pageDescriptors(5), reserveCover = true)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(0)),
                listOf(pageId(1), pageId(2)),
                listOf(pageId(3), pageId(4)),
            )
    }

    @Test
    fun `right to left visual placement reverses each spread without changing page identity`() {
        val original = pageDescriptors(4)

        PageGrouping.forMode(original, ReadingMode.DUAL_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(1), pageId(0)),
                listOf(pageId(3), pageId(2)),
            )

        original.map(PageDescriptor::id).shouldContainExactly(pageId(0), pageId(1), pageId(2), pageId(3))
    }

    @Test
    fun `spread snapshots stay immutable after a caller mutates its page list`() {
        val pages = pageDescriptors(4).toMutableList()
        val ltr = PageGrouping.dual(pages, reserveCover = false)
        val rtl = PageGrouping.forMode(pages, ReadingMode.DUAL_RTL, reserveCover = false)

        pages.clear()

        ltr.map { it.map(PageDescriptor::id) }.shouldContainExactly(
            listOf(pageId(0), pageId(1)),
            listOf(pageId(2), pageId(3)),
        )
        rtl.map { it.map(PageDescriptor::id) }.shouldContainExactly(
            listOf(pageId(1), pageId(0)),
            listOf(pageId(3), pageId(2)),
        )
    }

    @Test
    fun `all reader modes produce their concrete grouping and layout policies`() {
        val pages = pageDescriptors(4)

        PageGrouping.forMode(pages, ReadingMode.SINGLE_LTR, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.SINGLE_LTR) shouldBe ReaderLayoutPolicy(
            isRightToLeft = false,
            isDualPage = false,
            isContinuous = false,
            continuousGapPixels = 0,
            forcedScaleMode = null,
        )

        PageGrouping.forMode(pages, ReadingMode.SINGLE_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.SINGLE_RTL).isRightToLeft shouldBe true

        PageGrouping.forMode(pages, ReadingMode.DUAL_LTR, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0), pageId(1)), listOf(pageId(2), pageId(3)))
        ReaderLayout.policy(ReadingMode.DUAL_LTR).isDualPage shouldBe true

        PageGrouping.forMode(pages, ReadingMode.DUAL_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(1), pageId(0)), listOf(pageId(3), pageId(2)))
        ReaderLayout.policy(ReadingMode.DUAL_RTL) shouldBe ReaderLayoutPolicy(
            isRightToLeft = true,
            isDualPage = true,
            isContinuous = false,
            continuousGapPixels = 0,
            forcedScaleMode = null,
        )

        PageGrouping.forMode(pages, ReadingMode.VERTICAL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.VERTICAL) shouldBe ReaderLayoutPolicy(
            isRightToLeft = false,
            isDualPage = false,
            isContinuous = true,
            continuousGapPixels = ReaderLayout.DEFAULT_CONTINUOUS_GAP_PIXELS,
            forcedScaleMode = null,
        )

        PageGrouping.forMode(pages, ReadingMode.WEBTOON, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(listOf(pageId(0)), listOf(pageId(1)), listOf(pageId(2)), listOf(pageId(3)))
        ReaderLayout.policy(ReadingMode.WEBTOON) shouldBe ReaderLayoutPolicy(
            isRightToLeft = false,
            isDualPage = false,
            isContinuous = true,
            continuousGapPixels = 0,
            forcedScaleMode = ScaleMode.FIT_WIDTH,
        )
    }
}

private fun pageDescriptors(count: Int) = (0 until count).map { index ->
    PageDescriptor(id = pageId(index), width = 100, height = 200)
}

private fun pageId(index: Int) = PageId(chapterId = "chapter", entryName = "page-$index.jpg")
