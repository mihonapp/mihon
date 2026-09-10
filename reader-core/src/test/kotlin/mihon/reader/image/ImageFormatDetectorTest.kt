package mihon.reader.image

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImageFormatDetectorTest {
    @Test
    fun `detects raster formats from signatures`() {
        val cases = mapOf(
            bytes(0xff, 0xd8, 0xff, 0xe0) to ReaderImageFormat.JPEG,
            bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) to ReaderImageFormat.PNG,
            "GIF87a".encodeToByteArray() to ReaderImageFormat.GIF,
            "GIF89a".encodeToByteArray() to ReaderImageFormat.GIF,
            ("RIFF" + "0000" + "WEBP").encodeToByteArray() to ReaderImageFormat.WEBP,
            bytes(0xff, 0x0a, 0x12, 0x34) to ReaderImageFormat.JXL,
            bytes(0x00, 0x00, 0x00, 0x0c, 0x4a, 0x58, 0x4c, 0x20, 0x0d, 0x0a, 0x87, 0x0a) to ReaderImageFormat.JXL,
            "BMmore".encodeToByteArray() to ReaderImageFormat.BMP,
            bytes(0x49, 0x49, 0x2a, 0x00) to ReaderImageFormat.TIFF,
            bytes(0x4d, 0x4d, 0x00, 0x2a) to ReaderImageFormat.TIFF,
        )

        cases.forEach { (header, expected) -> ImageFormatDetector.detect(header) shouldBe expected }
    }

    @Test
    fun `detects avif and heif brands anywhere in bounded ftyp box`() {
        bmff("avif") shouldDetect ReaderImageFormat.AVIF
        bmff("avis") shouldDetect ReaderImageFormat.AVIF
        bmff("mif1", "av01") shouldDetect ReaderImageFormat.AVIF
        listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1").forEach { brand ->
            bmff(brand) shouldDetect ReaderImageFormat.HEIF
        }
    }

    @Test
    fun `rejects incomplete lookalike and unknown headers`() {
        byteArrayOf() shouldDetect ReaderImageFormat.UNKNOWN
        "RIFFshort".encodeToByteArray() shouldDetect ReaderImageFormat.UNKNOWN
        bmff("mp42") shouldDetect ReaderImageFormat.UNKNOWN
        ByteArray(65).also {
            "ftyp".encodeToByteArray().copyInto(it, 56)
            "avif".encodeToByteArray().copyInto(it, 60)
        } shouldDetect ReaderImageFormat.UNKNOWN
    }

    private infix fun ByteArray.shouldDetect(format: ReaderImageFormat) {
        ImageFormatDetector.detect(this) shouldBe format
    }

    private fun bmff(majorBrand: String, vararg compatibleBrands: String): ByteArray {
        val size = 16 + compatibleBrands.size * 4
        return ByteArray(size).also { bytes ->
            bytes[3] = size.toByte()
            "ftyp".encodeToByteArray().copyInto(bytes, 4)
            majorBrand.encodeToByteArray().copyInto(bytes, 8)
            compatibleBrands.forEachIndexed { index, brand ->
                brand.encodeToByteArray().copyInto(bytes, 16 + index * 4)
            }
        }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
