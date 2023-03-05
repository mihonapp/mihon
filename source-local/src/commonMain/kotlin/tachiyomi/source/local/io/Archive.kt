package tachiyomi.source.local.io

import java.io.File

object Archive {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "epub")

    fun isSupported(file: File): Boolean = with(file) {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}
