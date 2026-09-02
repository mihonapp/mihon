package mihon.reader.image

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import org.junit.jupiter.api.Test

class TilePlannerTest {
    private val pageId = PageId("1", "page.png")

    @Test
    fun `grid aligned visible region is covered exactly with one tile margin clipped to the image`() {
        val requests = TilePlanner.plan(
            pageId = pageId,
            frameId = null,
            imageWidth = 2048,
            imageHeight = 2048,
            visible = IntRect(0, 0, 1024, 1024),
        )

        requests.map { it.key.bounds }.shouldContainExactlyInAnyOrder(
            IntRect(0, 0, 1024, 1024),
            IntRect(1024, 0, 2048, 1024),
            IntRect(0, 1024, 1024, 2048),
            IntRect(1024, 1024, 2048, 2048),
        )
        requests.forEach { request ->
            request.key.pageId shouldBe pageId
            request.key.frameId shouldBe null
            request.key.sampleSize shouldBe 1
            request.targetWidth shouldBe 1024
            request.targetHeight shouldBe 1024
        }
    }

    @Test
    fun `edge tiles are clipped to the image bounds`() {
        val requests = TilePlanner.plan(
            pageId = pageId,
            frameId = null,
            imageWidth = 2500,
            imageHeight = 1500,
            visible = IntRect(0, 0, 2500, 1500),
        )

        requests.map { it.key.bounds }.shouldContainExactlyInAnyOrder(
            IntRect(0, 0, 1024, 1024),
            IntRect(1024, 0, 2048, 1024),
            IntRect(2048, 0, 2500, 1024),
            IntRect(0, 1024, 1024, 1500),
            IntRect(1024, 1024, 2048, 1500),
            IntRect(2048, 1024, 2500, 1500),
        )
    }

    @Test
    fun `one tile margin expands the visible region by exactly one grid cell`() {
        val requests = TilePlanner.plan(
            pageId = pageId,
            frameId = null,
            imageWidth = 4096,
            imageHeight = 4096,
            visible = IntRect(1024, 1024, 2048, 2048),
        )

        requests.map { it.key.bounds }.shouldContainExactlyInAnyOrder(
            IntRect(0, 0, 1024, 1024),
            IntRect(1024, 0, 2048, 1024),
            IntRect(2048, 0, 3072, 1024),
            IntRect(0, 1024, 1024, 2048),
            IntRect(1024, 1024, 2048, 2048),
            IntRect(2048, 1024, 3072, 2048),
            IntRect(0, 2048, 1024, 3072),
            IntRect(1024, 2048, 2048, 3072),
            IntRect(2048, 2048, 3072, 3072),
        )
    }

    @Test
    fun `visible region partially outside the image is clipped before planning`() {
        val requests = TilePlanner.plan(
            pageId = pageId,
            frameId = null,
            imageWidth = 2500,
            imageHeight = 1500,
            visible = IntRect(2000, 1000, 4000, 3000),
        )

        requests.map { it.key.bounds }.shouldContainExactlyInAnyOrder(
            IntRect(0, 0, 1024, 1024),
            IntRect(1024, 0, 2048, 1024),
            IntRect(2048, 0, 2500, 1024),
            IntRect(0, 1024, 1024, 1500),
            IntRect(1024, 1024, 2048, 1500),
            IntRect(2048, 1024, 2500, 1500),
        )
    }

    @Test
    fun `visible region entirely outside the image yields no tiles`() {
        TilePlanner.plan(
            pageId = pageId,
            frameId = null,
            imageWidth = 2500,
            imageHeight = 1500,
            visible = IntRect(3000, 2000, 4000, 3000),
        ) shouldBe emptyList()
    }

    @Test
    fun `keys are deduplicated and every coordinate stays inside the image`() {
        val frameId = FrameId(pageId, 2)
        val requests = TilePlanner.plan(
            pageId = pageId,
            frameId = frameId,
            imageWidth = 1025,
            imageHeight = 1023,
            visible = IntRect(0, 0, 1025, 1023),
            sampleSize = 2,
        )

        requests.size shouldBe requests.distinctBy { it.key }.size
        requests.map { it.key.bounds }.shouldContainExactlyInAnyOrder(
            IntRect(0, 0, 1024, 1023),
            IntRect(1024, 0, 1025, 1023),
        )
        requests.forEach { request ->
            request.key.frameId shouldBe frameId
            request.key.sampleSize shouldBe 2
            request.key.bounds.right shouldBe request.key.bounds.right.coerceAtMost(1025)
            request.key.bounds.bottom shouldBe request.key.bounds.bottom.coerceAtMost(1023)
            request.targetWidth shouldBe (request.key.bounds.width + 1) / 2
            request.targetHeight shouldBe (request.key.bounds.height + 1) / 2
        }
    }
}
