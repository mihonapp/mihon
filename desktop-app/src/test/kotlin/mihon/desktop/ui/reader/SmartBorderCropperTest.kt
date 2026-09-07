package mihon.desktop.ui.reader

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class SmartBorderCropperTest {

    @Test
    fun `crops white borders around dark content`() {
        val original = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g = original.createGraphics()
        try {
            // 10px white border on all sides
            g.color = Color.WHITE
            g.fillRect(0, 0, 100, 100)
            // Dark content inside
            g.color = Color.BLACK
            g.fillRect(10, 10, 80, 80)
        } finally {
            g.dispose()
        }

        val cropped = SmartBorderCropper.crop(original)

        // Borders should be trimmed to ~80x80
        cropped.width shouldBe 80
        cropped.height shouldBe 80
    }

    @Test
    fun `crops black borders around light content`() {
        val original = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g = original.createGraphics()
        try {
            // 10px black border on all sides
            g.color = Color.BLACK
            g.fillRect(0, 0, 100, 100)
            // White content inside
            g.color = Color.WHITE
            g.fillRect(10, 10, 80, 80)
        } finally {
            g.dispose()
        }

        val cropped = SmartBorderCropper.crop(original)

        cropped.width shouldBe 80
        cropped.height shouldBe 80
    }

    @Test
    fun `does not crop image without borders`() {
        val original = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g = original.createGraphics()
        try {
            // Mixed content with no uniform border
            g.color = Color.BLUE
            g.fillRect(0, 0, 100, 100)
        } finally {
            g.dispose()
        }

        val cropped = SmartBorderCropper.crop(original)

        cropped.width shouldBe 100
        cropped.height shouldBe 100
    }

    @Test
    fun `limits crop to safe max margin 20 percent`() {
        val original = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g = original.createGraphics()
        try {
            // 40px white border, 20px dark center (border > 20%)
            g.color = Color.WHITE
            g.fillRect(0, 0, 100, 100)
            g.color = Color.BLACK
            g.fillRect(40, 40, 20, 20)
        } finally {
            g.dispose()
        }

        val cropped = SmartBorderCropper.crop(original)

        // Max crop is 20% on each side -> min dimension is 60x60
        cropped.width shouldBe 60
        cropped.height shouldBe 60
    }

    @Test
    fun `does not crop very small images`() {
        val original = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val cropped = SmartBorderCropper.crop(original)
        cropped shouldBe original
    }
}
