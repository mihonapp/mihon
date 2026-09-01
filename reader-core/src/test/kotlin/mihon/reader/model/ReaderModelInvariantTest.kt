package mihon.reader.model

import io.kotest.assertions.throwables.shouldThrow
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
}
