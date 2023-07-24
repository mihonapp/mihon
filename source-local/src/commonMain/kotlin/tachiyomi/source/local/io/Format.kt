package tachiyomi.source.local.io

import java.io.File

sealed interface Format {
    data class Directory(val file: File) : Format
    data class Zip(val file: File) : Format
    data class Rar(val file: File) : Format
    data class Epub(val file: File) : Format

    class UnknownFormatException : Exception()

    companion object {

        fun valueOf(file: File) = with(file) {
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
