package tachiyomi.translation.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Downloads a recognition preset into app storage, verifies it and unpacks the tar.
 *
 * Deliberately closed: only [OcrPresetCatalog] ids are accepted, so a mismatched manifest can
 * never reach the engine and produce silent garbage.
 */
class OcrModelStore(
    private val context: Context,
    private val callFactory: Call.Factory,
) {
    data class Installed(
        val modelFile: File,
        val charsetFile: File,
        val charset: List<String>,
    )

    private fun presetDir(presetId: String): File =
        File(context.filesDir, "translation/models/$presetId")

    fun isInstalled(presetId: String): Boolean = resolve(presetId) != null

    fun installedBytes(presetId: String): Long {
        val dir = presetDir(presetId)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun resolve(presetId: String): Installed? {
        val dir = presetDir(presetId)
        if (!dir.exists()) return null
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val model = files.firstOrNull { it.extension.equals("onnx", true) } ?: return null
        val charsetFile = files.firstOrNull {
            it.extension.equals("txt", true) && it.length() > 64
        } ?: files.firstOrNull { it.name.equals("inference.yml", true) } ?: return null
        val charset = readCharset(charsetFile)
        if (charset.size < 100) return null
        return Installed(model, charsetFile, charset)
    }

    fun isModelInstalled(presetId: String): Boolean = resolveModel(presetId) != null

    /** Detection models ship without a charset, so only the onnx file is required. */
    fun resolveModel(presetId: String): File? {
        val dir = presetDir(presetId)
        if (!dir.exists()) return null
        return dir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("onnx", true) }
    }

    suspend fun download(
        preset: OcrPreset,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = presetDir(preset.id)
            dir.mkdirs()

            if (preset.isDirectFile) {
                // RapidOCR hosts weights and dictionary as plain files; there is no tar to unpack.
                fetch(OcrPresetCatalog.downloadUrl(preset), File(dir, preset.fileName), preset.sha256) {
                    onProgress(it * 0.9f)
                }
                val charsetUrl = preset.charsetUrl ?: error("model has no dictionary")
                fetch(charsetUrl, File(dir, preset.charsetFileName ?: "dict.txt"), preset.charsetSha256) {
                    onProgress(0.9f + it * 0.1f)
                }
            } else {
                val archive = File(dir, preset.fileName)
                fetch(OcrPresetCatalog.downloadUrl(preset), archive, preset.sha256, onProgress)
                extract(archive, dir)
                archive.delete()
            }

            // Detection tars carry no dictionary, so they only need the onnx file.
            val ready = if (preset.task == OcrPreset.TASK_DET) {
                resolveModel(preset.id) != null
            } else {
                resolve(preset.id) != null
            }
            check(ready) { "model file or dictionary missing" }
            onProgress(1f)
        }
    }

    /** Streams [url] into [target], verifying the checksum before the file becomes visible. */
    private fun fetch(
        url: String,
        target: File,
        sha256: String?,
        onProgress: (Float) -> Unit = {},
    ) {
        val temp = File(target.parentFile, "${target.name}.part")
        val request = Request.Builder().url(url).build()
        callFactory.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var written = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.fd.sync()
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha256 != null && !actual.equals(sha256, ignoreCase = true)) {
                temp.delete()
                error("checksum mismatch")
            }
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) error("cannot store ${target.name}")
    }

    fun delete(presetId: String) {
        presetDir(presetId).deleteRecursively()
    }

    private fun extract(archive: File, target: File) {
        val input = if (archive.name.endsWith(".gz", true)) {
            TarArchiveInputStream(GZIPInputStream(archive.inputStream().buffered()))
        } else {
            TarArchiveInputStream(archive.inputStream().buffered())
        }
        input.use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name.isNotEmpty()) {
                    val out = File(target, name)
                    FileOutputStream(out).use { output -> tar.copyTo(output) }
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun readCharset(file: File): List<String> {
        if (file.name.endsWith(".yml", true)) {
            val lines = file.readLines(Charsets.UTF_8)
            val start = lines.indexOfFirst { it.trim().startsWith("character_dict:") }
            if (start >= 0) {
                return parseYmlCharset(lines.drop(start + 1).takeWhile { it.startsWith(" ") || it.startsWith("-") })
            }
            return emptyList()
        }
        return OnnxOcrEngine.loadCharset(file)
    }

    companion object {
        /**
         * Parses a PaddleX `character_dict` block. Every entry must survive intact: the model's
         * class indices map 1:1 onto this list, so a single dropped entry shifts every later
         * character and silently kills the space class (PP-OCRv6 carries entries like `- '"'`,
         * `- ''''` and an ideographic space that the previous lossy parser destroyed).
         */
        internal fun parseYmlCharset(rawLines: List<String>): List<String> = rawLines.mapNotNull { line ->
            val item = line.trimStart(' ').removePrefix("-").trimStart(' ')
            val content = when {
                item.length >= 2 && item.first() == '\'' && item.last() == '\'' ->
                    item.substring(1, item.length - 1).replace("''", "'")
                item.length >= 2 && item.first() == '"' && item.last() == '"' ->
                    item.substring(1, item.length - 1)
                else -> item.trimEnd(' ')
            }
            content.takeIf { it.isNotEmpty() }
        }
    }
}
