package mihon.reader.image

import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode

internal object ImageFixtures {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )

    data class GifFrameSpec(
        val image: BufferedImage,
        val left: Int,
        val top: Int,
        val disposal: String,
        val delayHundredths: Int,
        val transparentIndex: Int = -1,
    )

    fun opaqueJpeg(width: Int, height: Int, rgb: Int = 0x7A3F20): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = java.awt.Color(rgb)
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return output.toByteArray()
    }

    fun alphaPng(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = if (x == 0 && y == 0) 0 else 0xFF
                image.setRGB(x, y, (alpha shl 24) or 0x224466)
            }
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    fun solidPng(width: Int, height: Int, argb: Int = 0xFF102030.toInt()): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = java.awt.Color(argb, true)
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    /** Pixel (x, y) encodes its coordinates as r = x & 0xFF, g = y & 0xFF for subsampling assertions. */
    fun coordinatePng(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, (0xFF shl 24) or ((x and 0xFF) shl 16) or ((y and 0xFF) shl 8))
            }
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun indexedFrame(
        width: Int,
        height: Int,
        red: ByteArray,
        green: ByteArray,
        blue: ByteArray,
        transparentIndex: Int,
        pixels: Array<IntArray>,
    ): BufferedImage {
        val model = if (transparentIndex >= 0) {
            IndexColorModel(8, red.size, red, green, blue, transparentIndex)
        } else {
            IndexColorModel(8, red.size, red, green, blue)
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, model)
        pixels.forEachIndexed { y, row ->
            row.forEachIndexed { x, index -> image.raster.setSample(x, y, 0, index) }
        }
        return image
    }

    private fun solidIndexed(width: Int, height: Int, argb: Int): BufferedImage {
        val r = ((argb shr 16) and 0xFF).toByte()
        val g = ((argb shr 8) and 0xFF).toByte()
        val b = (argb and 0xFF).toByte()
        val model = IndexColorModel(8, 2, byteArrayOf(r, r), byteArrayOf(g, g), byteArrayOf(b, b))
        return BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, model)
    }

    private fun localColorTable(image: BufferedImage): IIOMetadataNode {
        val model = image.colorModel as IndexColorModel
        val table = IIOMetadataNode("LocalColorTable")
        table.setAttribute("sizeOfLocalColorTable", model.mapSize.toString())
        table.setAttribute("sortFlag", "FALSE")
        for (index in 0 until model.mapSize) {
            val entry = IIOMetadataNode("ColorTableEntry")
            entry.setAttribute("index", index.toString())
            entry.setAttribute("red", model.getRed(index).toString())
            entry.setAttribute("green", model.getGreen(index).toString())
            entry.setAttribute("blue", model.getBlue(index).toString())
            table.appendChild(entry)
        }
        return table
    }

    /**
     * Four 4x4 logical-screen frames exercising transparency and every disposal mode:
     * 0: full opaque red, doNotDispose, 50 ms.
     * 1: 2x2 blue at (1,1) with a transparent top-left pixel, restoreToBackgroundColor, 20 s (clamps to 10 s).
     * 2: 2x2 opaque green at (0,0), restoreToPrevious, 10 ms (clamps to 20 ms).
     * 3: 1x1 opaque yellow at (3,3), none, 1000 ms.
     */
    fun animatedGif(): ByteArray {
        val red = solidIndexed(4, 4, 0xFF0000)
        val blueTransparent = indexedFrame(
            2,
            2,
            byteArrayOf(0, 0),
            byteArrayOf(0, 0),
            byteArrayOf(0, 0xFF.toByte()),
            transparentIndex = 0,
            pixels = arrayOf(intArrayOf(0, 1), intArrayOf(1, 1)),
        )
        val green = solidIndexed(2, 2, 0x00FF00)
        val yellow = solidIndexed(1, 1, 0xFFFF00)
        val frames = listOf(
            GifFrameSpec(red, 0, 0, "doNotDispose", 5),
            GifFrameSpec(blueTransparent, 1, 1, "restoreToBackgroundColor", 2000, transparentIndex = 0),
            GifFrameSpec(green, 0, 0, "restoreToPrevious", 1),
            GifFrameSpec(yellow, 3, 3, "none", 100),
        )

        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val output = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(output).use { imageOutput ->
            writer.output = imageOutput
            writer.prepareWriteSequence(null)
            val param: ImageWriteParam = writer.defaultWriteParam
            frames.forEach { frame ->
                val metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(frame.image),
                    param,
                )
                val root = IIOMetadataNode("javax_imageio_gif_image_1.0")
                val descriptor = IIOMetadataNode("ImageDescriptor")
                descriptor.setAttribute("imageLeftPosition", frame.left.toString())
                descriptor.setAttribute("imageTopPosition", frame.top.toString())
                descriptor.setAttribute("imageWidth", frame.image.width.toString())
                descriptor.setAttribute("imageHeight", frame.image.height.toString())
                descriptor.setAttribute("interlaceFlag", "FALSE")
                root.appendChild(descriptor)
                root.appendChild(localColorTable(frame.image))
                val control = IIOMetadataNode("GraphicControlExtension")
                control.setAttribute("disposalMethod", frame.disposal)
                control.setAttribute("userInputFlag", "FALSE")
                control.setAttribute(
                    "transparentColorFlag",
                    if (frame.transparentIndex >= 0) "TRUE" else "FALSE",
                )
                control.setAttribute("delayTime", frame.delayHundredths.toString())
                control.setAttribute("transparentColorIndex", frame.transparentIndex.coerceAtLeast(0).toString())
                root.appendChild(control)
                metadata.mergeTree("javax_imageio_gif_image_1.0", root)
                writer.writeToSequence(IIOImage(frame.image, null, metadata), param)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return output.toByteArray()
    }

    /**
     * A structurally valid single-frame 1x1 GIF whose logical screen descriptor claims a
     * [width] x [height] composition canvas (both at most 65,535, the GIF u16 limit).
     */
    fun forgedGifCanvas(width: Int, height: Int): ByteArray {
        require(width in 1..65_535 && height in 1..65_535) { "gif logical screen is a u16" }
        val output = ByteArrayOutputStream()
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        output.write(width and 0xFF)
        output.write((width shr 8) and 0xFF)
        output.write(height and 0xFF)
        output.write((height shr 8) and 0xFF)
        output.write(0x80) // global color table present, 2 entries
        output.write(0) // background color index
        output.write(0) // pixel aspect ratio
        output.write(byteArrayOf(0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        // Minimal 1x1 image: descriptor, LZW minimum code size 2, one data sub-block.
        output.write(byteArrayOf(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0))
        output.write(0x3B) // trailer
        return output.toByteArray()
    }

    /** Valid PNG signature followed by garbage: a reader exists but header parsing must fail. */
    fun corruptPngBytes(): ByteArray = PNG_SIGNATURE + byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

    fun notAnImageBytes(): ByteArray = "this is not an image at all".toByteArray()

    /** A structurally valid PNG header with forged dimensions and no pixel data. */
    fun writeForgedPngHeader(path: Path, width: Int, height: Int) {
        Files.newOutputStream(path).buffered().use { output ->
            output.write(PNG_SIGNATURE)
            writeChunk(output, "IHDR", ihdr(width, height))
            writeChunk(output, "IEND", ByteArray(0))
        }
    }

    /**
     * Writes a grayscale PNG scanline by scanline without ever constructing its raster.
     * Row y is filled with byte value `y % 251` so decoded regions remain verifiable.
     */
    fun writeStreamedGrayscalePng(path: Path, width: Int, height: Int) {
        Files.newOutputStream(path).buffered(1 shl 16).use { output ->
            output.write(PNG_SIGNATURE)
            writeChunk(output, "IHDR", ihdr(width, height))
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed, Deflater(Deflater.BEST_COMPRESSION), 1 shl 16).use { deflater ->
                val row = ByteArray(width + 1)
                for (y in 0 until height) {
                    Arrays.fill(row, 1, row.size, (y % 251).toByte())
                    deflater.write(row)
                }
            }
            val compressedBytes = compressed.toByteArray()
            var offset = 0
            while (offset < compressedBytes.size) {
                val length = minOf(1 shl 16, compressedBytes.size - offset)
                writeChunk(output, "IDAT", compressedBytes, offset, length)
                offset += length
            }
            writeChunk(output, "IEND", ByteArray(0))
        }
    }

    private fun ihdr(width: Int, height: Int): ByteArray {
        val buffer = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(width)
        buffer.putInt(height)
        buffer.put(8) // bit depth
        buffer.put(0) // color type: grayscale
        buffer.put(0) // compression
        buffer.put(0) // filter
        buffer.put(0) // interlace
        return buffer.array()
    }

    private fun writeChunk(
        output: OutputStream,
        type: String,
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length)
        output.write(lengthBytes.array())
        output.write(typeBytes)
        output.write(data, offset, length)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data, offset, length)
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt())
        output.write(crcBytes.array())
    }
}
