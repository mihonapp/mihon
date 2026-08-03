package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

class LocalSourceFileSystem(
    private val storageManager: StorageManager,
) {

    fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalSourceDirectory()
    }

    fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    fun getMangaDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
    }

    fun getFilesInMangaDirectory(name: String): List<UniFile> {
        return getMangaDirectory(name)?.listFiles().orEmpty().toList()
    }
}
