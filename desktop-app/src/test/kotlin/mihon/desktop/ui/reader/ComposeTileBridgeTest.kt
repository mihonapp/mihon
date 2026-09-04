package mihon.desktop.ui.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.reader.image.IntRect
import mihon.reader.image.TileKey
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeTileBridgeTest {

    @Test
    fun `same visible tile converts once and disposes after its last lease`() = runTest {
        var conversions = 0
        var disposals = 0
        val bridge = ComposeTileBridge(
            converter = ComposeImageConverter {
                conversions++
                ComposeBridgeImage(ImageBitmap(1, 1)) { disposals++ }
            },
        )
        val key = key("page.png")
        val image = image(2, 2)

        val first = bridge.acquire(key, image)
        val second = bridge.acquire(key, image)

        conversions shouldBe 1
        bridge.metrics.recordCount shouldBe 1
        bridge.metrics.retainedBytes shouldBe 16L
        first.image shouldBe second.image
        first.close()
        disposals shouldBe 0
        second.close()
        disposals shouldBe 1
        bridge.metrics.retainedBytes shouldBe 0L
        bridge.close()
    }

    @Test
    fun `bridge converts distinct selected frame pixels`() = runTest {
        val bridge = ComposeTileBridge()
        val red = image(1, 1, Color.RED.rgb)
        val blue = image(1, 1, Color.BLUE.rgb)

        val redLease = bridge.acquire(key("animated.gif", frame = 0), red)
        val blueLease = bridge.acquire(key("animated.gif", frame = 1), blue)

        redLease.image.toPixelMap()[0, 0] shouldBe androidx.compose.ui.graphics.Color.Red
        blueLease.image.toPixelMap()[0, 0] shouldBe androidx.compose.ui.graphics.Color.Blue
        redLease.close()
        blueLease.close()
        bridge.close()
    }

    @Test
    fun `two dual-page animation swaps reserve replacements before releasing current frames`() = runTest {
        val frameBytes = 16L * MIB
        val image = image(1, 1)
        val entered = mutableListOf<CompletableDeferred<Unit>>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val bridge = ComposeTileBridge(
            converter = ComposeImageConverter {
                calls++
                if (calls > 2) {
                    CompletableDeferred<Unit>().also(entered::add).complete(Unit)
                    release.await()
                }
                ComposeBridgeImage(ImageBitmap(1, 1))
            },
            byteCount = { _, _ -> frameBytes },
        )
        val left = bridge.acquire(key("left.gif", frame = 0), image)
        val right = bridge.acquire(key("right.gif", frame = 0), image)

        val leftSwap = async { bridge.replaceAnimated(left, key("left.gif", frame = 1), image) }
        val rightSwap = async { bridge.replaceAnimated(right, key("right.gif", frame = 1), image) }
        runCurrent()

        entered.size shouldBe 2
        bridge.metrics.retainedBytes shouldBe 64L * MIB
        bridge.metrics.highWaterBytes shouldBe 64L * MIB
        release.complete(Unit)
        runCurrent()
        leftSwap.await().close()
        rightSwap.await().close()
        bridge.metrics.retainedBytes shouldBe 0L
        bridge.close()
    }

    @Test
    fun `static replacement releases old record before waiting under pressure`() = runTest {
        val bridge = ComposeTileBridge(limitBytes = 8, byteCount = { _, _ -> 8 })
        val first = bridge.acquire(key("first.png"), image(1, 1))

        val replacement = bridge.replaceStatic(first, key("second.png"), image(1, 1))

        replacement.key shouldBe key("second.png")
        bridge.metrics.retainedBytes shouldBe 8L
        replacement.close()
        bridge.close()
    }

    @Test
    fun `tiled dual pages stop at 96 MiB and a cancelled waiter leaks no bytes`() = runTest {
        val tileBytes = 4L * MIB
        val bridge = ComposeTileBridge(byteCount = { _, _ -> tileBytes })
        val tiles = (0 until 24).map { index ->
            bridge.acquire(key("page-${index / 12}.png", left = index), image(1, 1))
        }
        bridge.metrics.retainedBytes shouldBe 96L * MIB
        bridge.metrics.highWaterBytes shouldBe 96L * MIB

        val waiting = async { bridge.acquire(key("pressure.png"), image(1, 1)) }
        runCurrent()
        bridge.metrics.waitingCount shouldBe 1
        waiting.cancelAndJoin()
        bridge.metrics.waitingCount shouldBe 0

        tiles.forEach(AutoCloseable::close)
        bridge.metrics.retainedBytes shouldBe 0L
        bridge.metrics.highWaterBytes shouldBe 96L * MIB
        bridge.close()
    }

    private fun key(name: String, frame: Int? = null, left: Int = 0): TileKey {
        val page = PageId("chapter", name)
        return TileKey(
            pageId = page,
            frameId = frame?.let { FrameId(page, it) },
            bounds = IntRect(left, 0, left + 1, 1),
        )
    }

    private fun image(width: Int, height: Int, rgb: Int = Color.BLACK.rgb) =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, rgb) }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
