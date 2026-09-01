package mihon.reader.layout

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class NaturalPageComparatorTest {
    @Test
    fun `natural order is case insensitive and numeric aware with deterministic numeric ties`() {
        listOf("10.jpg", "02.jpg", "2.jpg", "1.jpg", "a.jpg", "A.jpg")
            .sortedWith(NaturalPageComparator)
            .shouldContainExactly("1.jpg", "2.jpg", "02.jpg", "10.jpg", "A.jpg", "a.jpg")
    }

    @Test
    fun `natural order normalizes nested path separators`() {
        listOf("chapter\\10.jpg", "chapter/2.jpg", "chapter\\1.jpg", "another/1.jpg")
            .sortedWith(NaturalPageComparator)
            .shouldContainExactly("another/1.jpg", "chapter\\1.jpg", "chapter/2.jpg", "chapter\\10.jpg")
    }

    @Test
    fun `natural order has a deterministic raw tie break after separator normalization`() {
        listOf("chapter\\1.jpg", "chapter/1.jpg")
            .sortedWith(NaturalPageComparator)
            .shouldContainExactly("chapter/1.jpg", "chapter\\1.jpg")
    }

    @Test
    fun `natural order produces a deterministic tie break for equal case folded names`() {
        listOf("PAGE.jpg", "page.jpg", "Page.jpg")
            .sortedWith(NaturalPageComparator)
            .shouldContainExactly("PAGE.jpg", "Page.jpg", "page.jpg")
    }
}
