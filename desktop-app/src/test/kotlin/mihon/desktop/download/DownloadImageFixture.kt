package mihon.desktop.download

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal fun validDownloadImage(): ByteArray = ByteArrayOutputStream().use { output ->
    ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
    output.toByteArray()
}
