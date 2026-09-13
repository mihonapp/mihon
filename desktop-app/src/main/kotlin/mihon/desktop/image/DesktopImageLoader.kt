package mihon.desktop.image

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.reader.codec.PackagedReaderCodec
import mihon.reader.image.ImageFormatDetector
import mihon.reader.image.ReaderImageFormat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

val LocalImageLoader = staticCompositionLocalOf<DesktopImageLoader?> { null }
val LocalCustomCoverManager = staticCompositionLocalOf<CustomCoverManager?> { null }

data class ImageRequest(
    val uri: String?,
    val mangaId: Long? = null,
    val localMangaPath: Path? = null,
    val headers: Map<String, String> = emptyMap(),
)

class DesktopImageLoader(
    private val diskCacheDir: Path,
    private val client: OkHttpClient = OkHttpClient(),
    private val customCoverManager: CustomCoverManager? = null,
    private val maxMemoryEntries: Int = 120,
) {
    private val memoryCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(maxMemoryEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > maxMemoryEntries
            }
        },
    )

    init {
        Files.createDirectories(diskCacheDir)
    }

    suspend fun load(request: ImageRequest): ImageBitmap? = withContext(Dispatchers.IO) {
        // 1. Check custom cover first if mangaId is present
        if (request.mangaId != null && customCoverManager != null) {
            val custom = customCoverManager.getCustomCover(request.mangaId)
            if (custom != null) {
                val cacheKey = "custom_${request.mangaId}_${Files.getLastModifiedTime(custom).toMillis()}"
                memoryCache[cacheKey]?.let { return@withContext it }
                val bitmap = decodeFile(custom)
                if (bitmap != null) {
                    memoryCache[cacheKey] = bitmap
                    return@withContext bitmap
                }
            }
        }

        // 2. Determine URI to load
        val uriStr = request.uri?.trim()
        if (uriStr.isNullOrBlank()) {
            if (request.localMangaPath != null) {
                return@withContext loadFromLocalManga(request.localMangaPath)
            }
            return@withContext null
        }

        val memKey = "uri_$uriStr"
        memoryCache[memKey]?.let { return@withContext it }

        // 3. Network URL
        if (uriStr.startsWith("http://", ignoreCase = true) || uriStr.startsWith("https://", ignoreCase = true)) {
            val bitmap = loadFromNetwork(uriStr, request.headers)
            if (bitmap != null) {
                memoryCache[memKey] = bitmap
            }
            return@withContext bitmap
        }

        // 4. File URI or Path
        val filePath = try {
            if (uriStr.startsWith("file://", ignoreCase = true)) {
                Path.of(URI(uriStr))
            } else {
                Path.of(uriStr)
            }
        } catch (_: Exception) {
            null
        }

        if (filePath != null) {
            val bitmap = loadFromLocalPath(filePath)
            if (bitmap != null) {
                memoryCache[memKey] = bitmap
            }
            return@withContext bitmap
        }

        if (request.localMangaPath != null) {
            return@withContext loadFromLocalManga(request.localMangaPath)
        }

        null
    }

    private fun loadFromLocalPath(path: Path): ImageBitmap? {
        if (!Files.exists(path)) return null
        if (Files.isDirectory(path)) {
            return loadFromLocalManga(path)
        }
        val filename = path.fileName.toString().lowercase()
        if (filename.endsWith(".cbz") || filename.endsWith(".zip")) {
            return extractCoverFromArchive(path)
        }
        return decodeFile(path)
    }

    private fun loadFromLocalManga(mangaDirOrArchive: Path): ImageBitmap? {
        if (!Files.exists(mangaDirOrArchive)) return null
        if (Files.isRegularFile(mangaDirOrArchive)) {
            return extractCoverFromArchive(mangaDirOrArchive)
        }
        val coverNames = listOf(
            "cover.jpg", "cover.png", "cover.webp", "cover.jpeg", "cover.gif", "cover.avif",
            "cover.heic", "cover.heif", "cover.jxl", "cover.bmp", "cover.tif", "cover.tiff", "Folder.jpg",
        )
        for (name in coverNames) {
            val candidate = mangaDirOrArchive.resolve(name)
            if (Files.isRegularFile(candidate)) {
                return decodeFile(candidate)
            }
        }
        try {
            Files.list(mangaDirOrArchive).use { stream ->
                val files = stream.toList().sortedBy { it.fileName.toString().lowercase() }
                for (file in files) {
                    val fn = file.fileName.toString().lowercase()
                    if (isImageFile(fn)) {
                        return decodeFile(file)
                    } else if (fn.endsWith(".cbz") || fn.endsWith(".zip")) {
                        val fromZip = extractCoverFromArchive(file)
                        if (fromZip != null) return fromZip
                    } else if (Files.isDirectory(file)) {
                        val fromSub = loadFromLocalManga(file)
                        if (fromSub != null) return fromSub
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractCoverFromArchive(zipPath: Path): ImageBitmap? {
        try {
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && isImageFile(it.name.lowercase()) }
                    .sortedBy { it.name.lowercase() }
                    .toList()
                val first = entries.firstOrNull() ?: return null
                zip.getInputStream(first).use { input ->
                    val bytes = input.readAllBytes()
                    return decodeBytes(bytes)
                }
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun loadFromNetwork(url: String, customHeaders: Map<String, String>): ImageBitmap? {
        val hash = sha256(url)
        val diskFile = diskCacheDir.resolve("$hash.img")

        // Check disk cache
        if (Files.isRegularFile(diskFile) && Files.size(diskFile) > 0) {
            val cachedBitmap = decodeFile(diskFile)
            if (cachedBitmap != null) return cachedBitmap
            Files.deleteIfExists(diskFile)
        }

        // Fetch from network
        try {
            val reqBuilder = Request.Builder().url(url)
            reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MihonW/1.0")
            customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body
                val bytes = body.bytes()
                if (bytes.isEmpty()) return null

                val bitmap = decodeBytes(bytes) ?: return null

                // Save to disk cache atomically
                val tmpFile = diskCacheDir.resolve("$hash.tmp")
                Files.write(tmpFile, bytes)
                Files.move(tmpFile, diskFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

                return bitmap
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            return null
        }
    }

    private fun decodeFile(file: Path): ImageBitmap? {
        return try {
            val bytes = Files.readAllBytes(file)
            decodeBytes(bytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBytes(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        val native = try {
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
        if (native != null) return native

        return when (ImageFormatDetector.detect(bytes)) {
            ReaderImageFormat.AVIF,
            ReaderImageFormat.HEIF,
            ReaderImageFormat.JXL,
            ReaderImageFormat.TIFF,
            -> decodeWithPackagedCodec(bytes)
            else -> null
        }
    }

    /** Uses the same bundled ImageMagick runtime as the reader for formats Skia cannot decode. */
    private fun decodeWithPackagedCodec(bytes: ByteArray): ImageBitmap? {
        val executable = PackagedReaderCodec.executablePath()
        if (!Files.isRegularFile(executable)) return null
        val input = Files.createTempFile(diskCacheDir, "cover-", ".img")
        val output = Files.createTempFile(diskCacheDir, "cover-", ".png")
        val error = Files.createTempFile(diskCacheDir, "cover-", ".log")
        return try {
            Files.write(input, bytes)
            val process = ProcessBuilder(
                executable.toString(),
                "-limit", "memory", "128MiB",
                "-limit", "map", "0",
                "-limit", "disk", "256MiB",
                input.toString(),
                "-auto-orient",
                "-delete", "1--1",
                "PNG:$output",
            ).redirectError(error.toFile()).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0 || Files.size(output) == 0L || Files.size(output) > MAX_FALLBACK_BYTES) {
                return null
            }
            val png = Files.readAllBytes(output)
            Image.makeFromEncoded(png).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        } finally {
            Files.deleteIfExists(input)
            Files.deleteIfExists(output)
            Files.deleteIfExists(error)
        }
    }

    private fun isImageFile(name: String): Boolean =
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
            name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".avif") ||
            name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".jxl") ||
            name.endsWith(".bmp") || name.endsWith(".tif") || name.endsWith(".tiff")

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getFromMemory(key: String): ImageBitmap? = memoryCache[key]

    fun putInMemory(key: String, bitmap: ImageBitmap) {
        memoryCache[key] = bitmap
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }

    fun getCoverFile(mangaId: Long?, url: String?): Path? {
        if (mangaId != null && customCoverManager != null) {
            val custom = customCoverManager.getCustomCover(mangaId)
            if (custom != null && Files.isRegularFile(custom)) return custom
        }
        val trimmed = url?.trim() ?: return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val hash = sha256(trimmed)
            val diskFile = diskCacheDir.resolve("$hash.img")
            if (Files.isRegularFile(diskFile) && Files.size(diskFile) > 0) return diskFile
        } else {
            val candidate = try {
                Path.of(trimmed)
            } catch (_: Exception) {
                null
            }
            if (candidate != null && Files.isRegularFile(candidate)) return candidate
        }
        return null
    }

    fun clearDiskCache() {
        try {
            Files.list(diskCacheDir).use { stream ->
                stream.forEach { Files.deleteIfExists(it) }
            }
        } catch (_: Exception) {}
    }

    private companion object {
        const val MAX_FALLBACK_BYTES = 64L * 1024L * 1024L
    }
}
