package android.graphics

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class BitmapCompatibilityTest {
    @Test
    fun `source interceptors can decode mutable pixels and encode image without losing replacements`() {
        val original = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
        val encoded = ByteArrayOutputStream().also { ImageIO.write(original, "png", it) }.toByteArray()
        val options = BitmapFactory.Options().apply { inMutable = true }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options))
        bitmap.getWidth() shouldBe 3
        bitmap.getHeight() shouldBe 2
        bitmap.setPixels(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt()), 0, 2, 1, 0, 2, 1)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) shouldBe true
        val result = ImageIO.read(output.toByteArray().inputStream())
        result.getRGB(1, 0) shouldBe 0xffff0000.toInt()
        result.getRGB(2, 0) shouldBe 0xff00ff00.toInt()
        bitmap.recycle()
    }
}
