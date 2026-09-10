package mihon.reader.source

import java.util.Locale

object ImageEntryPolicy {
    private val drivePrefix = Regex("^[A-Za-z]:")
    private val extensions = setOf(
        "png",
        "apng",
        "jpg",
        "jpeg",
        "gif",
        "webp",
        "avif",
        "heif",
        "heic",
        "jxl",
        "bmp",
        "wbmp",
        "tif",
        "tiff",
    )

    fun normalize(rawName: String): String {
        if (rawName.isBlank() || '\u0000' in rawName) throw ReaderFailure.UnsafePath(rawName, "empty or NUL name")
        val unixName = rawName.replace('\\', '/')
        if (unixName.startsWith('/') || unixName.startsWith("//") || drivePrefix.containsMatchIn(unixName)) {
            throw ReaderFailure.UnsafePath(rawName, "absolute, UNC, or drive-prefixed name")
        }
        val segments = unixName.split('/')
        if (segments.any { it == ".." }) throw ReaderFailure.UnsafePath(rawName, "parent traversal")
        val normalized = segments.filterNot { it.isEmpty() || it == "." }.joinToString("/")
        if (normalized.isEmpty()) throw ReaderFailure.UnsafePath(rawName, "empty normalized name")
        return normalized
    }

    fun duplicateKey(normalizedName: String): String = normalizedName.lowercase(Locale.ROOT)

    fun isSupportedImage(name: String): Boolean =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT) in extensions
}
