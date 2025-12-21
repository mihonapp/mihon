package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension
import tachiyomi.source.local.io.Archive.isSupported as isArchiveSupported

sealed interface Format {
    data class Directory(val file: UniFile) : Format
    data class Archive(val file: UniFile) : Format
    data class Epub(val file: UniFile) : Format
    data class Text(val file: UniFile) : Format
    data class Html(val file: UniFile) : Format

    class UnknownFormatException : Exception()

    companion object {
        private val TEXT_EXTENSIONS = listOf("txt", "text")
        private val HTML_EXTENSIONS = listOf("html", "htm", "xhtml")

        fun valueOf(file: UniFile) = when {
            file.isDirectory -> Directory(file)
            file.extension.equals("epub", true) -> Epub(file)
            TEXT_EXTENSIONS.any { file.extension.equals(it, true) } -> Text(file)
            HTML_EXTENSIONS.any { file.extension.equals(it, true) } -> Html(file)
            isArchiveSupported(file) -> Archive(file)
            else -> throw UnknownFormatException()
        }

        fun isTextFormat(file: UniFile): Boolean {
            return TEXT_EXTENSIONS.any { file.extension.equals(it, true) } ||
                HTML_EXTENSIONS.any { file.extension.equals(it, true) }
        }
    }
}
