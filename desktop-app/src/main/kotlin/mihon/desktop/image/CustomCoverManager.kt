package mihon.desktop.image

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CustomCoverManager(
    private val coversDir: Path,
) {
    val customDir: Path = coversDir.resolve("custom")

    init {
        Files.createDirectories(customDir)
    }

    fun getCustomCover(mangaId: Long): Path? {
        val extensions = listOf(".jpg", ".png", ".webp", ".jpeg")
        for (ext in extensions) {
            val file = customDir.resolve("custom_$mangaId$ext")
            if (Files.isRegularFile(file) && Files.size(file) > 0) {
                return file
            }
        }
        return null
    }

    fun setCustomCover(mangaId: Long, sourceFile: Path): Path {
        require(Files.isRegularFile(sourceFile)) { "Source file must be a regular file: $sourceFile" }
        removeCustomCover(mangaId)
        val ext = sourceFile.fileName.toString().substringAfterLast('.', "jpg").lowercase()
        val target = customDir.resolve("custom_$mangaId.$ext")
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    fun removeCustomCover(mangaId: Long): Boolean {
        var removed = false
        val extensions = listOf(".jpg", ".png", ".webp", ".jpeg")
        for (ext in extensions) {
            val file = customDir.resolve("custom_$mangaId$ext")
            if (Files.deleteIfExists(file)) {
                removed = true
            }
        }
        return removed
    }

    fun hasCustomCover(mangaId: Long): Boolean = getCustomCover(mangaId) != null
}
