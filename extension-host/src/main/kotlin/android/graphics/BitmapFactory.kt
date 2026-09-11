package android.graphics

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

object BitmapFactory {
    class Options {
        @JvmField var inMutable = false

        @JvmField var inJustDecodeBounds = false

        @JvmField var outWidth = -1

        @JvmField var outHeight = -1
    }

    @JvmStatic
    @JvmOverloads
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, options: Options? = null): Bitmap? {
        require(offset >= 0 && length >= 0 && offset.toLong() + length <= data.size)
        ImageIO.createImageInputStream(ByteArrayInputStream(data, offset, length)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                options?.outWidth = width
                options?.outHeight = height
                if (options?.inJustDecodeBounds == true) return null
                require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000) {
                    "Bitmap exceeds pixel limit"
                }
                return Bitmap(reader.read(0))
            } finally {
                reader.dispose()
            }
        }
    }
}
