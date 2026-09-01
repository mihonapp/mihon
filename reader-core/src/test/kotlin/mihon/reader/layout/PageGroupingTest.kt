package mihon.reader.layout

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReadingMode
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

        PageGrouping.forMode(original, ReadingMode.DOUBLE_PAGE_RTL, reserveCover = false)
            .map { it.map(PageDescriptor::id) }
            .shouldContainExactly(
                listOf(pageId(1), pageId(0)),
                listOf(pageId(3), pageId(2)),
            )

        original.map(PageDescriptor::id).shouldContainExactly(pageId(0), pageId(1), pageId(2), pageId(3))
    }

    @Test
    fun `all reader modes have explicit grouping and direction contracts`() {
        ReadingMode.entries.shouldContainExactly(
            ReadingMode.LEFT_TO_RIGHT,
            ReadingMode.RIGHT_TO_LEFT,
            ReadingMode.VERTICAL,
            ReadingMode.WEBTOON,
            ReadingMode.DOUBLE_PAGE_LTR,
            ReadingMode.DOUBLE_PAGE_RTL,
        )
        ReadingMode.RIGHT_TO_LEFT.isRightToLeft shouldBe true
        ReadingMode.DOUBLE_PAGE_RTL.isRightToLeft shouldBe true
        ReadingMode.DOUBLE_PAGE_LTR.isDualPage shouldBe true
        ReadingMode.DOUBLE_PAGE_RTL.isDualPage shouldBe true
    }
}

private fun pageDescriptors(count: Int) = (0 until count).map { index ->
    PageDescriptor(id = pageId(index), width = 100, height = 200)
}

private fun pageId(index: Int) = PageId(chapterId = "chapter", entryName = "page-$index.jpg")
