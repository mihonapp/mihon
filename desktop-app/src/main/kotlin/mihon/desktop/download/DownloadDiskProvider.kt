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
            false // An unavailable download volume must not be treated as writable.
        }
    }

    fun getPageFile(tempDir: Path, pageIndex: Int, extension: String = "jpg"): Path {
        val ext = if (extension.startsWith(".")) extension.substring(1) else extension
        val fileName = String.format("%03d.%s", pageIndex + 1, ext)
        return tempDir.resolve(fileName)
    }

    fun savePage(tempDir: Path, pageIndex: Int, bytes: ByteArray, extension: String = "jpg"): Path {
        Files.createDirectories(tempDir)
        val file = getPageFile(tempDir, pageIndex, extension)
        val partial = file.resolveSibling("${file.fileName}.part")
        try {
            Files.write(partial, bytes)
            if (!isValidPage(partial)) throw IOException("Page ${pageIndex + 1} is corrupt or incomplete")
            try {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING)
            }
            return file
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /** Decode a bounded sample instead of trusting length or a format signature. */
    fun isValidPage(file: Path): Boolean = runCatching {
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return@runCatching false
        if (!hasCompleteContainer(file)) return@runCatching false
        javax.imageio.ImageIO.createImageInputStream(file.toFile()).use { input ->
            if (input == null) return@runCatching false
            val readers = javax.imageio.ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                // The same native codec family used by the desktop reader covers WebP/AVIF.
                org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(file)).use { image ->
                    return@runCatching image.width > 0 && image.height > 0
                }
            }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0) return@runCatching false
                val params = reader.defaultReadParam
                val sample = ((maxOf(width, height) + 1023L) / 1024L).toInt().coerceAtLeast(1)
                params.setSourceSubsampling(sample, sample, 0, 0)
                val decoded = reader.read(0, params) ?: return@runCatching false
                decoded.flush()
                true
            } finally {
                reader.dispose()
            }
        }
    }.getOrDefault(false)

    private fun hasCompleteContainer(file: Path): Boolean = java.io.RandomAccessFile(file.toFile(), "r").use { input ->
        if (input.length() < 12) return@use false
        val header = ByteArray(12).also(input::readFully)
        when {
            header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() -> {
                input.seek(input.length() - 2)
                input.readUnsignedShort() == 0xffd9
            }
            header[0] == 0x89.toByte() && header.copyOfRange(1, 4).toString(Charsets.US_ASCII) == "PNG" -> {
                input.seek(input.length() - 12)
                val end = ByteArray(12).also(input::readFully)
                end.toList() == listOf<Byte>(0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)
            }
            header.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "GIF" -> {
                input.seek(input.length() - 1)
                input.read() == 0x3b
            }
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" -> {
                val size = (4..7).fold(0L) { value, index ->
                    value or
                        ((header[index].toLong() and 255) shl ((index - 4) * 8))
                }
                size + 8 == input.length()
            }
            else -> true
        }
    }
    fun cleanPartialPages(tempDir: Path) {
        if (!Files.isDirectory(tempDir)) return
        Files.list(tempDir).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".part") }.forEach { Files.deleteIfExists(it) }
        }
    }

    fun deleteTempChapter(sourceId: Long, mangaTitle: String, chapterName: String) {
        deleteDirectory(getTempChapterDir(sourceId, mangaTitle, chapterName))
    }

    private fun deleteDirectory(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
    fun isChapterDownloaded(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        return Files.exists(chapterDir) && Files.isDirectory(chapterDir)
    }

    fun deleteChapter(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        if (!Files.exists(chapterDir)) return false
        return try {
            deleteDirectory(chapterDir)
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

        // Every page must remain decodable before publishing an offline chapter.
        var totalBytes = 0L
        for (i in 0 until totalPages) {
            val pagePath = getPageFile(tempDir, i)
            if (!isValidPage(pagePath)) {
                throw IOException("Page $i ($pagePath) is missing, corrupt or incomplete")
            }
            totalBytes += Files.size(pagePath)
        }

        cleanPartialPages(tempDir)
        val previousDir = targetDir.resolveSibling("${targetDir.fileName}.previous")
        // Keep the previous complete chapter until replacement succeeds.
        if (Files.exists(targetDir)) {
            deleteDirectory(previousDir)
            Files.move(targetDir, previousDir)
        }
        try {
            try {
                Files.move(tempDir, targetDir, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempDir, targetDir)
            }
        } catch (error: Exception) {
            if (!Files.exists(targetDir) && Files.exists(previousDir)) {
                Files.move(previousDir, targetDir)
            }
            throw error
        }
        deleteDirectory(previousDir)
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
