package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.storage.extension

sealed interface Format {
    data class Directory(val file: UniFile) : Format
    data class Zip(val file: UniFile) : Format
    data class Rar(val file: UniFile) : Format
    data class Epub(val file: UniFile) : Format

    class UnknownFormatException : Exception()

    companion object {

        fun valueOf(file: UniFile) = with(file) {
            when {
                isDirectory -> Directory(this)
                extension.equals("zip", true) || extension.equals("cbz", true) -> Zip(this)
                extension.equals("rar", true) || extension.equals("cbr", true) -> Rar(this)
                extension.equals("epub", true) -> Epub(this)
                else -> throw UnknownFormatException()
            }
        }
    }
}
