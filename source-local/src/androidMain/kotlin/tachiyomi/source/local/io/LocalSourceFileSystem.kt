package tachiyomi.source.local.io

import tachiyomi.core.provider.FolderProvider
import java.io.File

actual class LocalSourceFileSystem(
    private val folderProvider: FolderProvider,
) {

    actual fun getBaseDirectory(): File {
        return File(folderProvider.directory(), "local")
    }

    actual fun getFilesInBaseDirectory(): List<File> {
        return getBaseDirectory().listFiles().orEmpty().toList()
    }

    actual fun getMangaDirectory(name: String): File? {
        return getFilesInBaseDirectory()
            // Get the first mangaDir or null
            .firstOrNull { it.isDirectory && it.name == name }
    }

    actual fun getFilesInMangaDirectory(name: String): List<File> {
        return getFilesInBaseDirectory()
            // Filter out ones that are not related to the manga and is not a directory
            .filter { it.isDirectory && it.name == name }
            // Get all the files inside the filtered folders
            .flatMap { it.listFiles().orEmpty().toList() }
    }
}
