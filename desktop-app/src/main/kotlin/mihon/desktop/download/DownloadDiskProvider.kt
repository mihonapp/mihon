package mihon.desktop.download

import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.repository.LibraryMutationPort
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DownloadDiskProvider(
    val downloadsDir: Path,
    private val minDiskSpaceBytes: Long = 50L * 1024 * 1024, // 50 MB safety margin
) {
    init {
        if (!Files.exists(downloadsDir)) {
            Files.createDirectories(downloadsDir)
        }
    }

    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return sanitized.ifEmpty { "unnamed" }
    }

    fun getMangaDir(sourceId: Long, mangaTitle: String): Path {
        return downloadsDir.resolve(sourceId.toString()).resolve(sanitizeFileName(mangaTitle))
    }

    fun getChapterDir(sourceId: Long, mangaTitle: String, chapterName: String): Path {
        return getMangaDir(sourceId, mangaTitle).resolve(sanitizeFileName(chapterName))
    }

    fun getTempChapterDir(sourceId: Long, mangaTitle: String, chapterName: String): Path {
        return getMangaDir(sourceId, mangaTitle).resolve("${sanitizeFileName(chapterName)}_tmp")
    }

    fun checkDiskSpace(requiredBytes: Long = minDiskSpaceBytes): Boolean {
        return try {
            val store = Files.getFileStore(downloadsDir)
            store.usableSpace >= requiredBytes
        } catch (e: Exception) {
            true // Fallback if getFileStore fails on certain virtual drives
        }
    }

    fun getPageFile(tempDir: Path, pageIndex: Int, extension: String = "jpg"): Path {
        val ext = if (extension.startsWith(".")) extension.substring(1) else extension
        val fileName = String.format("%03d.%s", pageIndex + 1, ext)
        return tempDir.resolve(fileName)
    }

    fun savePage(tempDir: Path, pageIndex: Int, bytes: ByteArray, extension: String = "jpg"): Path {
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir)
        }
        val file = getPageFile(tempDir, pageIndex, extension)
        Files.write(file, bytes)
        return file
    }

    fun isChapterDownloaded(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        return Files.exists(chapterDir) && Files.isDirectory(chapterDir)
    }

    fun deleteChapter(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        if (!Files.exists(chapterDir)) return false
        return try {
            Files.walk(chapterDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun finalizeChapter(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalPages: Int,
        mutationPort: LibraryMutationPort? = null,
    ): Path {
        val tempDir = getTempChapterDir(sourceId, mangaTitle, chapterName)
        val targetDir = getChapterDir(sourceId, mangaTitle, chapterName)

        if (!Files.exists(tempDir) || !Files.isDirectory(tempDir)) {
            throw IOException("Temporary download directory $tempDir does not exist")
        }

        // Verify that all pages exist and are non-empty
        var totalBytes = 0L
        for (i in 0 until totalPages) {
            val pagePath = getPageFile(tempDir, i)
            if (!Files.exists(pagePath) || Files.size(pagePath) == 0L) {
                throw IOException("Page $i ($pagePath) is missing or empty")
            }
            totalBytes += Files.size(pagePath)
        }

        // If targetDir already exists, delete it first to ensure clean atomic move
        if (Files.exists(targetDir)) {
            Files.walk(targetDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }

        try {
            Files.move(tempDir, targetDir, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tempDir, targetDir, StandardCopyOption.REPLACE_EXISTING)
        }

        // Register as local chapter asset in database so reader-core can open it immediately offline
        mutationPort?.let { port ->
            val mangaDir = getMangaDir(sourceId, mangaTitle)
            port.insertLocalManga(
                LocalMangaRecord(
                    mangaId = mangaId,
                    storagePath = mangaDir.toAbsolutePath().toString(),
                    manifestSha256 = "",
                    importedAt = System.currentTimeMillis(),
                ),
            )
            port.insertLocalChapter(
                LocalChapterRecord(
                    chapterId = chapterId,
                    relativePath = sanitizeFileName(chapterName),
                    assetKind = "DIRECTORY",
                    sizeBytes = totalBytes,
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
        }

        return targetDir
    }
}
