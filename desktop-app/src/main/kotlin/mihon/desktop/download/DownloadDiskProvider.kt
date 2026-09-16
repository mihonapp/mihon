package mihon.desktop.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.library.model.LocalChapterRecord
import mihon.desktop.library.model.LocalMangaRecord
import mihon.desktop.library.repository.LibraryMutationPort
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
private data class DownloadChapterManifest(
    val version: Int = 1,
    val totalPages: Int,
    val pages: List<DownloadedPageMetadata>,
)

@Serializable
internal data class DownloadedPageMetadata(
    val index: Int,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

internal data class DownloadChapterInspection(
    val expectedPages: Int,
    val validPages: Map<Int, DownloadedPageMetadata>,
) {
    val isComplete: Boolean
        get() = expectedPages > 0 && validPages.size == expectedPages

    val totalBytes: Long
        get() = validPages.values.sumOf(DownloadedPageMetadata::sizeBytes)
}

class DownloadDiskProvider(
    val downloadsDir: Path,
    private val minDiskSpaceBytes: Long = 50L * 1024 * 1024, // 50 MB safety margin
) {
    private val manifestJson = Json { ignoreUnknownKeys = true }

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

    /**
     * Verifies a published chapter against its cheap completion manifest. Legacy or changed files
     * are decoded once and receive a fresh manifest so later startup checks remain inexpensive.
     */
    internal fun inspectChapter(
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        expectedPageIndexes: List<Int>,
    ): DownloadChapterInspection {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        if (!Files.isDirectory(chapterDir) || expectedPageIndexes.isEmpty()) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val expected = expectedPageIndexes.distinct().sorted()
        if (expected.size != expectedPageIndexes.size) {
            return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        }
        val manifest = readManifest(chapterDir)?.takeIf { saved ->
            saved.version == MANIFEST_VERSION &&
                saved.totalPages == expected.size &&
                saved.pages.map(DownloadedPageMetadata::index).sorted() == expected
        }
        val manifestPages = manifest?.pages?.associateBy(DownloadedPageMetadata::index).orEmpty()
        val valid = buildMap {
            expected.forEach { index ->
                val path = getPageFile(chapterDir, index)
                val saved = manifestPages[index]
                val unchanged = saved != null && runCatching {
                    Files.isRegularFile(path) &&
                        Files.size(path) == saved.sizeBytes &&
                        Files.getLastModifiedTime(path).toMillis() == saved.modifiedAtMillis
                }.getOrDefault(false)
                if (unchanged || isValidPage(path)) {
                    pageMetadata(path, index)?.let { put(index, it) }
                }
            }
        }
        if (valid.size == expected.size) {
            val current = valid.values.sortedBy(DownloadedPageMetadata::index)
            if (manifest?.pages != current) runCatching { writeManifest(chapterDir, current) }
        }
        return DownloadChapterInspection(expected.size, valid)
    }

    fun isChapterDownloaded(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        if (!Files.isDirectory(chapterDir)) return false
        val manifestFile = manifestPath(chapterDir)
        val manifest = readManifest(chapterDir)
        if (Files.exists(manifestFile) && manifest == null) return false
        if (manifest != null && manifest.totalPages > 0) {
            return inspectChapter(
                sourceId,
                mangaTitle,
                chapterName,
                manifest.pages.map(DownloadedPageMetadata::index),
            ).isComplete
        }
        // Older downloads may predate manifests. The saved queue migrates them during startup.
        return runCatching {
            Files.list(chapterDir).use { paths ->
                paths.anyMatch { path ->
                    Files.isRegularFile(path) &&
                        path.fileName.toString() != MANIFEST_FILE &&
                        !path.fileName.toString().endsWith(".part") &&
                        runCatching { Files.size(path) > 0L }.getOrDefault(false)
                }
            }
        }.getOrDefault(false)
    }

    fun deleteChapter(sourceId: Long, mangaTitle: String, chapterName: String): Boolean {
        val chapterDir = getChapterDir(sourceId, mangaTitle, chapterName)
        if (!Files.exists(chapterDir)) return false
        return try {
            deleteDirectory(chapterDir)
            runCatching {
                val mangaDir = chapterDir.parent
                if (Files.isDirectory(mangaDir) && Files.list(mangaDir).use { !it.findAny().isPresent }) {
                    Files.deleteIfExists(mangaDir)
                }
            }
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
        val metadata = mutableListOf<DownloadedPageMetadata>()
        for (i in 0 until totalPages) {
            val pagePath = getPageFile(tempDir, i)
            if (!isValidPage(pagePath)) {
                throw IOException("Page $i ($pagePath) is missing, corrupt or incomplete")
            }
            pageMetadata(pagePath, i)?.let(metadata::add)
                ?: throw IOException("Page $i ($pagePath) disappeared while finalizing")
        }
        val totalBytes = metadata.sumOf(DownloadedPageMetadata::sizeBytes)

        cleanPartialPages(tempDir)
        writeManifest(tempDir, metadata)
        val previousDir = targetDir.resolveSibling("${targetDir.fileName}.previous")
        // Keep the previous complete chapter until replacement succeeds.
        if (Files.exists(targetDir)) {
            deleteDirectory(previousDir)
            Files.move(targetDir, previousDir)
        }
        var published = false
        try {
            try {
                Files.move(tempDir, targetDir, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempDir, targetDir)
            }
            published = true
            // Commit both records together. A registration failure must leave the downloaded
            // pages retryable and retain any previously registered offline chapter.
            registerCompletedChapter(
                sourceId = sourceId,
                mangaId = mangaId,
                chapterId = chapterId,
                mangaTitle = mangaTitle,
                chapterName = chapterName,
                totalBytes = totalBytes,
                mutationPort = mutationPort,
            )
        } catch (error: Exception) {
            runCatching {
                if (published) Files.move(targetDir, tempDir)
                if (!Files.exists(targetDir) && Files.exists(previousDir)) {
                    Files.move(previousDir, targetDir)
                }
            }.onFailure(error::addSuppressed)
            throw error
        }
        deleteDirectory(previousDir)

        return targetDir
    }

    internal fun registerCompletedChapter(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalBytes: Long,
        mutationPort: LibraryMutationPort?,
    ) {
        mutationPort?.transaction {
            val mangaDir = getMangaDir(sourceId, mangaTitle)
            insertLocalManga(
                LocalMangaRecord(
                    mangaId = mangaId,
                    storagePath = mangaDir.toAbsolutePath().toString(),
                    manifestSha256 = "",
                    importedAt = System.currentTimeMillis(),
                ),
            )
            insertLocalChapter(
                LocalChapterRecord(
                    chapterId = chapterId,
                    relativePath = sanitizeFileName(chapterName),
                    assetKind = "DIRECTORY",
                    sizeBytes = totalBytes,
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    internal fun isChapterRegistrationCurrent(
        sourceId: Long,
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterName: String,
        totalBytes: Long,
        mutationPort: LibraryMutationPort,
    ): Boolean = mutationPort.isLocalChapterAssetRegistered(
        mangaId = mangaId,
        storagePath = getMangaDir(sourceId, mangaTitle).toAbsolutePath().toString(),
        chapterId = chapterId,
        relativePath = sanitizeFileName(chapterName),
        sizeBytes = totalBytes,
    )

    private fun pageMetadata(path: Path, index: Int): DownloadedPageMetadata? = runCatching {
        if (!Files.isRegularFile(path)) return@runCatching null
        DownloadedPageMetadata(
            index = index,
            sizeBytes = Files.size(path),
            modifiedAtMillis = Files.getLastModifiedTime(path).toMillis(),
        )
    }.getOrNull()

    private fun manifestPath(chapterDir: Path): Path = chapterDir.resolve(MANIFEST_FILE)

    private fun readManifest(chapterDir: Path): DownloadChapterManifest? = runCatching {
        val path = manifestPath(chapterDir)
        if (!Files.isRegularFile(path)) return@runCatching null
        manifestJson.decodeFromString<DownloadChapterManifest>(Files.readString(path))
    }.getOrNull()

    private fun writeManifest(chapterDir: Path, pages: List<DownloadedPageMetadata>) {
        val sorted = pages.sortedBy(DownloadedPageMetadata::index)
        val path = manifestPath(chapterDir)
        val partial = path.resolveSibling("${path.fileName}.part")
        Files.createDirectories(chapterDir)
        try {
            Files.writeString(
                partial,
                manifestJson.encodeToString(
                    DownloadChapterManifest(
                        version = MANIFEST_VERSION,
                        totalPages = sorted.size,
                        pages = sorted,
                    ),
                ),
            )
            try {
                Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    private companion object {
        const val MANIFEST_VERSION = 1
        const val MANIFEST_FILE = ".mihon-download.json"
    }
}
