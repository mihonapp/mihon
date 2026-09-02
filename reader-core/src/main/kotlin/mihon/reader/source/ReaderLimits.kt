package mihon.reader.source

object ReaderLimits {
    const val MAX_ENTRIES: Int = 100_000
    const val MAX_PAGE_BYTES: Long = 256L * 1024L * 1024L
    const val MAX_CHAPTER_EXPANDED_BYTES: Long = 2L * 1024L * 1024L * 1024L
    const val MAX_STANDALONE_BYTES: Long = 256L * 1024L * 1024L
    const val MAX_XML_BYTES: Long = 8L * 1024L * 1024L
    const val MAX_XML_DEPTH: Int = 64
    const val READER_MEMORY_BYTES: Long = 256L * 1024L * 1024L
    const val SEVEN_Z_MEMORY_KIB: Int = 131_072
    const val MAX_IMAGE_DIMENSION: Int = 200_000
    const val FULL_DECODE_MAX_BYTES: Long = 16L * 1024L * 1024L
}
