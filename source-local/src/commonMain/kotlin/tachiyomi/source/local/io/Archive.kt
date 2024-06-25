package tachiyomi.source.local.io

import com.hippo.unifile.UniFile

object Archive {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt", "epub")

    fun isSupported(file: UniFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val extension = name.substringAfterLast('.')
        val nameWithoutExtension = name.substringBeforeLast('.')
        return extension in SUPPORTED_ARCHIVE_TYPES
    }
}
