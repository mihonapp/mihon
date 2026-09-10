package mihon.reader.image

object ImageFormatDetector {
    private const val HEADER_LIMIT = 64

    fun detect(header: ByteArray): ReaderImageFormat {
        val size = minOf(header.size, HEADER_LIMIT)
        return when {
            header.matches(size, 0, 0xff, 0xd8, 0xff) -> ReaderImageFormat.JPEG
            header.matches(size, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> ReaderImageFormat.PNG
            header.asciiEquals(size, 0, "GIF87a") || header.asciiEquals(size, 0, "GIF89a") -> ReaderImageFormat.GIF
            header.asciiEquals(size, 0, "RIFF") && header.asciiEquals(size, 8, "WEBP") -> ReaderImageFormat.WEBP
            header.matches(size, 0, 0xff, 0x0a) ||
                header.matches(size, 0, 0x00, 0x00, 0x00, 0x0c, 0x4a, 0x58, 0x4c, 0x20, 0x0d, 0x0a, 0x87, 0x0a) ->
                ReaderImageFormat.JXL
            header.asciiEquals(size, 0, "BM") -> ReaderImageFormat.BMP
            header.matches(size, 0, 0x49, 0x49, 0x2a, 0x00) ||
                header.matches(size, 0, 0x4d, 0x4d, 0x00, 0x2a) -> ReaderImageFormat.TIFF
            header.asciiEquals(size, 4, "ftyp") -> detectIsoBmff(header, size)
            else -> ReaderImageFormat.UNKNOWN
        }
    }

    private fun detectIsoBmff(header: ByteArray, size: Int): ReaderImageFormat {
        val brands = buildList {
            if (size >= 12) add(header.ascii(8))
            var offset = 16
            while (offset + 4 <= size) {
                add(header.ascii(offset))
                offset += 4
            }
        }
        if (brands.any { it in avifBrands }) return ReaderImageFormat.AVIF
        return if (brands.any { it in heifBrands }) ReaderImageFormat.HEIF else ReaderImageFormat.UNKNOWN
    }

    private fun ByteArray.matches(size: Int, offset: Int, vararg signature: Int): Boolean =
        offset >= 0 && offset + signature.size <= size &&
            signature.indices.all { index -> this[offset + index].toInt() and 0xff == signature[index] }

    private fun ByteArray.asciiEquals(size: Int, offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= size &&
            value.indices.all { index -> this[offset + index].toInt() and 0xff == value[index].code }

    private fun ByteArray.ascii(offset: Int): String =
        CharArray(4) { index -> (this[offset + index].toInt() and 0xff).toChar() }.concatToString()

    private val avifBrands = setOf("avif", "avis", "av01")
    private val heifBrands = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
}
