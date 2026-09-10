package mihon.reader.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import mihon.reader.memory.BoundedReaderMemoryBudget
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import mihon.reader.source.BoundedPageInput
import mihon.reader.source.ReaderFailure
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO

class ApngPageDecoderTest {
    @Test
    fun `composites partial frames with over blend and previous disposal`() = runTest {
        val bytes = animation()
        val budget = BoundedReaderMemoryBudget(1024 * 1024)
        val decoder = ApngPageDecoder(budget)
        val pageId = PageId("chapter", "partial.png")
        val metadata = decoder.probe(input(bytes))

        metadata.frameCount shouldBe 3
        metadata.frameDurationsMillis shouldBe listOf(100L, 200L, 300L)
        metadata.hasAlpha shouldBe true

        decoder.decodeFull(input(bytes), metadata, FrameId(pageId, 1)).use { frame ->
            frame.image.getRGB(0, 0) shouldBe Color.RED.rgb
            frame.image.getRGB(1, 0) shouldBe Color.BLUE.rgb
        }
        decoder.decodeFull(input(bytes), metadata, FrameId(pageId, 2)).use { frame ->
            frame.image.getRGB(0, 0) shouldBe Color.GREEN.rgb
            frame.image.getRGB(1, 0) shouldBe Color.RED.rgb
        }
        budget.metrics.reservedBytes shouldBe 0
        budget.close()
    }

    @Test
    fun `rejects a chunk with a corrupt crc`() = runTest {
        val bytes = animation().also { it[it.lastIndex - 5] = (it[it.lastIndex - 5].toInt() xor 1).toByte() }
        val budget = BoundedReaderMemoryBudget(1024 * 1024)
        shouldThrow<ReaderFailure.CorruptImage> {
            ApngPageDecoder(budget).probe(input(bytes))
        }
        budget.close()
    }

    private fun animation(): ByteArray {
        val red = png(2, 1, Color.RED)
        val blue = png(1, 1, Color.BLUE)
        val green = png(1, 1, Color.GREEN)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(PNG_SIGNATURE)
                chunk(output, "IHDR", chunkData(red, "IHDR").single())
                chunk(output, "acTL", ints(3, 0))
                chunk(output, "fcTL", frameControl(0, 2, 1, 0, 0, 1, 10, 0, 0))
                chunkData(red, "IDAT").forEach { chunk(output, "IDAT", it) }
                chunk(output, "fcTL", frameControl(1, 1, 1, 1, 0, 2, 10, 2, 1))
                chunkData(blue, "IDAT").forEach { chunk(output, "fdAT", ints(2) + it) }
                chunk(output, "fcTL", frameControl(3, 1, 1, 0, 0, 3, 10, 0, 0))
                chunkData(green, "IDAT").forEach { chunk(output, "fdAT", ints(4) + it) }
                chunk(output, "IEND", byteArrayOf())
            }
            bytes.toByteArray()
        }
    }

    private fun png(width: Int, height: Int, color: Color): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            image.flush()
            output.toByteArray()
        }
    }

    private fun frameControl(
        sequence: Int,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        numerator: Int,
        denominator: Int,
        dispose: Int,
        blend: Int,
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            listOf(sequence, width, height, x, y).forEach(output::writeInt)
            output.writeShort(numerator)
            output.writeShort(denominator)
            output.writeByte(dispose)
            output.writeByte(blend)
        }
        bytes.toByteArray()
    }

    private fun ints(vararg values: Int): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output -> values.forEach(output::writeInt) }
        bytes.toByteArray()
    }

    private fun chunkData(png: ByteArray, wantedType: String): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var offset = PNG_SIGNATURE.size
        while (offset < png.size) {
            val length = readInt(png, offset)
            val type = png.copyOfRange(offset + 4, offset + 8).decodeToString()
            if (type == wantedType) result += png.copyOfRange(offset + 8, offset + 8 + length)
            offset += length + 12
        }
        return result
    }

    private fun chunk(output: DataOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.encodeToByteArray()
        output.writeInt(data.size)
        output.write(typeBytes)
        output.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        output.writeInt(crc.value.toInt())
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff shl 24) or
            (bytes[offset + 1].toInt() and 0xff shl 16) or
            (bytes[offset + 2].toInt() and 0xff shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun input(bytes: ByteArray) = BoundedPageInput(ByteArrayInputStream(bytes), bytes.size.toLong())

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
