package tachiyomi.source.local.io

import com.hippo.unifile.UniFile

object Archive {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "rar", "7z", "tar", "epub")

    fun isSupported(file: UniFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val extension = name.substringAfterLast('.')
        val nameWithoutExtension = name.substringBeforeLast('.')
        return extension in SUPPORTED_ARCHIVE_TYPES ||
            extension.startsWith("cb") || // cbz, cbr, etc.
            nameWithoutExtension.endsWith(".tar") // tar.gz, tar.bz2, etc.
    }
}
