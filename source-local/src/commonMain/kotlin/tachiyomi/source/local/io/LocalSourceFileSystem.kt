package tachiyomi.source.local.io

import java.io.File

expect class LocalSourceFileSystem {

    fun getBaseDirectory(): File

    fun getFilesInBaseDirectory(): List<File>

    fun getMangaDirectory(name: String): File?

    fun getFilesInMangaDirectory(name: String): List<File>
}
