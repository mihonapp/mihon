package android.graphics

import java.awt.image.BufferedImage
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/** JVM implementation of the pixel operations used by source image interceptors. */
class Bitmap internal constructor(private var image: BufferedImage?) {
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
    private fun pixels() = checkNotNull(image) { "Bitmap has been recycled" }
    fun getWidth(): Int = pixels().width
    fun getHeight(): Int = pixels().height
    fun setPixels(colors: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        pixels().setRGB(x, y, width, height, colors, offset, stride)
    }
    fun getPixels(colors: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        pixels().getRGB(x, y, width, height, colors, offset, stride)
    }
    fun recycle() {
        image?.flush()
        image = null
    }
    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        require(quality in 0..100)
        if (format == CompressFormat.PNG) return ImageIO.write(pixels(), "png", stream)
        if (format == CompressFormat.JPEG) {
            val rgb = BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB)
            rgb.createGraphics().let { graphics ->
                try {
                    graphics.drawImage(pixels(), 0, 0, null)
                } finally {
                    graphics.dispose()
                }
            }
            return ImageIO.write(rgb, "jpeg", stream)
        }
        val executable = System.getProperty("mihon.reader.codec")?.let(::File)
            ?: System.getProperty("compose.application.resources.dir")?.let { File(it, "codec/magick.exe") }
            ?: System.getenv("APPDIR")?.let { File(it, "resources/codec/magick.exe") }
            ?: error("Packaged WebP encoder is unavailable")
        require(executable.isFile) { "Packaged WebP encoder is unavailable" }
        val directory = java.nio.file.Files.createTempDirectory("mihon-bitmap-").toFile()
        try {
            val input = File(directory, "input.png")
            val output = File(directory, "output.webp")
            ImageIO.write(pixels(), "png", input)
            val process = ProcessBuilder(
                executable.absolutePath, "-limit", "memory", "256MiB", "-limit", "map", "0",
                "-limit", "disk", "256MiB", "-limit", "thread", "2", "-limit", "time", "30",
                input.absolutePath, "-quality", quality.toString(),
                "-define", "webp:lossless=${format == CompressFormat.WEBP_LOSSLESS}", output.absolutePath,
            ).redirectErrorStream(true).redirectOutput(File(directory, "codec.log")).start()
            try {
                check(process.waitFor(35, TimeUnit.SECONDS)) { "WebP encoding timed out" }
                check(process.exitValue() == 0 && output.isFile) { "WebP encoding failed" }
                output.inputStream().use { it.copyTo(stream) }
                return true
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor()
            }
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
