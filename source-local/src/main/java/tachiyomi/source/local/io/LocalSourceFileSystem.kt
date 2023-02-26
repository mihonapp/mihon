package tachiyomi.source.local.io

import java.io.File

interface LocalSourceFileSystem {

    fun getBaseDirectories(): Sequence<File>

    fun getFilesInBaseDirectories(): Sequence<File>

    fun getMangaDirectory(name: String): File?

    fun getFilesInMangaDirectory(name: String): Sequence<File>
}
