package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.storage.extension

sealed interface Format {
    data class Directory(val file: UniFile) : Format
    data class Zip(val file: UniFile) : Format
    data class SevenZip(val file: UniFile) : Format
    data class Rar(val file: UniFile) : Format
    data class Epub(val file: UniFile) : Format

    class UnknownFormatException : Exception()

    companion object {

        fun valueOf(file: UniFile): Format {
            if (file.isDirectory) return Directory(file)
            return when (file.extension?.lowercase()) {
                "zip", "cbz" -> Zip(file)
                "7z", "cb7" -> SevenZip(file)
                "rar", "cbr" -> Rar(file)
                "epub" -> Epub(file)
                else -> throw UnknownFormatException()
            }
        }
    }
}
