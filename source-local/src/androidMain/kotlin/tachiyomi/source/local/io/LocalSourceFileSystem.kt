package tachiyomi.source.local.io

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import tachiyomi.core.storage.FolderProvider

actual class LocalSourceFileSystem(
    private val context: Context,
    private val folderProvider: FolderProvider,
) {

    actual fun getBaseDirectory(): UniFile? {
        return UniFile.fromUri(context, folderProvider.path().toUri())
            ?.createDirectory("local")
    }

    actual fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    actual fun getMangaDirectory(name: String): UniFile? {
        return getFilesInBaseDirectory()
            // Get the first mangaDir or null
            .firstOrNull { it.isDirectory && it.name == name }
    }

    actual fun getFilesInMangaDirectory(name: String): List<UniFile> {
        return getFilesInBaseDirectory()
            // Filter out ones that are not related to the manga and is not a directory
            .filter { it.isDirectory && it.name == name }
            // Get all the files inside the filtered folders
            .flatMap { it.listFiles().orEmpty().toList() }
    }
}
