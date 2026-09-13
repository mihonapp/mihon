package mihon.desktop.ui.reader

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.reader.image.IntRect
import mihon.reader.image.TilePlanner
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderViewport
import org.junit.jupiter.api.Test

class TiledReaderPageTest {
    private val page = PageDescriptor(PageId("chapter", "large.png"), 8000, 12000)

    @Test
    fun `viewport maps pan to original pixels and selects bounded sampling`() {
        val context = calculatePageRenderContext(
            page = page,
            viewport = ReaderViewport(1000, 800),
            transform = ReaderPageTransform(
                widthPixels = 4000,
                heightPixels = 6000,
                zoom = 1f,
                pan = ReaderPan(1500f, -2000f),
            ),
        )

        context.visibleImageBounds shouldBe IntRect(0, 9200, 2000, 10800)
        context.sampleSize shouldBe 2
    }

    @Test
    fun `planner adds exactly one tile margin around the visible viewport`() {
        val requests = TilePlanner.plan(
            pageId = page.id,
            frameId = null,
            imageWidth = page.width,
            imageHeight = page.height,
            visible = IntRect(2048, 4096, 3072, 5120),
        )

        requests.map { it.key.bounds }.shouldContainExactly(
            IntRect(1024, 3072, 2048, 4096),
            IntRect(2048, 3072, 3072, 4096),
            IntRect(3072, 3072, 4096, 4096),
            IntRect(1024, 4096, 2048, 5120),
            IntRect(2048, 4096, 3072, 5120),
            IntRect(3072, 4096, 4096, 5120),
            IntRect(1024, 5120, 2048, 6144),
            IntRect(2048, 5120, 3072, 6144),
            IntRect(3072, 5120, 4096, 6144),
        )
    }
}
