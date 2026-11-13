package tachiyomi.translation.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Request
import java.io.File

/**
 * Downloads the Manga OCR weights. The model is `muscgab/manga-ocr-base-CPT` (continued
 * pre-training of `kha-white/manga-ocr-base` with a replaced vocabulary), taken from an ONNX
 * export published by the community.
 *
 * Hugging Face is not reachable from every network, so every file is tried against the primary
 * host and then the `hf-mirror.com` mirror.
 */
class MangaOcrStore(
    private val context: Context,
    private val callFactory: Call.Factory,
) {
    fun dir(): File = File(context.filesDir, "translation/manga-ocr")

    fun encoder(): File? = listOf("encoder.onnx")
        .map { File(dir(), it) }
        .firstOrNull { it.isFile && it.length() > 0 }

    fun decoder(): File? = File(dir(), "decoder.onnx").takeIf { it.isFile && it.length() > 0 }

    fun vocab(): File? = File(dir(), "vocab.txt").takeIf { it.isFile && it.length() > 0 }

    fun isInstalled(): Boolean = encoder() != null && decoder() != null && vocab() != null

    fun installedBytes(): Long = runCatching {
        dir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    fun delete() {
        runCatching { dir().deleteRecursively() }
    }

    suspend fun download(onProgress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = dir().apply { mkdirs() }
            onProgress(0f)

            // Keep whatever a previous attempt already fetched: the weights are the slow part.
            if (encoder() == null) fetchFirst(ENCODER_URLS, File(target, "encoder.onnx"), MIN_ENCODER_BYTES)
            onProgress(0.5f)

            if (decoder() == null) fetchFirst(DECODER_URLS, File(target, "decoder.onnx"), MIN_DECODER_BYTES)
            onProgress(0.85f)

            downloadVocab(target)
            onProgress(1f)
        }
    }

    private fun fetchFirst(urls: List<String>, target: File, minBytes: Long) {
        val failures = ArrayList<String>(urls.size)
        for (url in urls) {
            val label = url.substringAfterLast('/')
            val ok = runCatching {
                callFactory.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        failures += "$label HTTP ${response.code}"
                        return@use false
                    }
                    val body = response.body ?: return@use false
                    // Reject JSON error pages and tiny placeholder files.
                    if (body.contentLength() in 1 until minBytes) {
                        failures += "$label 文件过小"
                        return@use false
                    }

                    val temp = File(target.parentFile, "${target.name}.part")
                    body.byteStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (temp.length() < minBytes) {
                        temp.delete()
                        failures += "$label 内容不完整"
                        return@use false
                    }
                    if (!temp.renameTo(target)) {
                        temp.delete()
                        failures += "$label 写入失败"
                        return@use false
                    }
                    true
                }
            }.getOrElse {
                failures += "$label ${it.message ?: "网络错误"}"
                false
            }
            if (ok) return
        }
        error("${target.name} 下载失败：${failures.joinToString("；")}")
    }

    private fun downloadVocab(target: File) {
        val vocab = File(target, "vocab.txt")
        val failures = ArrayList<String>(VOCAB_URLS.size)

        for (url in VOCAB_URLS) {
            val label = url.substringAfterLast('/')
            val ok = runCatching {
                callFactory.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        failures += "$label HTTP ${response.code}"
                        return@use false
                    }
                    val text = response.body?.string() ?: return@use false
                    if (text.length < 500) {
                        failures += "$label 内容过短"
                        return@use false
                    }

                    val lines = if (label.endsWith(".json", ignoreCase = true)) {
                        parseTokenizer(text) ?: run {
                            failures += "$label 无法解析词表"
                            return@use false
                        }
                    } else {
                        text.lineSequence().map { it.trimEnd('\r') }.toList()
                    }
                    if (lines.size < 500) {
                        failures += "$label 词条过少(${lines.size})"
                        return@use false
                    }
                    vocab.outputStream().use { out ->
                        lines.forEach { line -> out.write((line + "\n").toByteArray()) }
                    }
                    true
                }
            }.getOrElse {
                failures += "$label ${it.message ?: "网络错误"}"
                false
            }
            if (ok) return
        }
        error("词表下载失败：${failures.joinToString("；")}")
    }

    /** Pulls `model.vocab` out of a HuggingFace tokenizer.json, in id order. */
    private fun parseTokenizer(text: String): List<String>? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val model = root["model"]?.jsonObject ?: return@runCatching null
        val vocab = model["vocab"] ?: return@runCatching null
        val byId = when (vocab) {
            is kotlinx.serialization.json.JsonObject -> vocab.entries
                .map { (token, id) -> id.jsonPrimitive.int to token }
                .sortedBy { it.first }
                .map { it.second }

            is kotlinx.serialization.json.JsonArray -> vocab
                .mapNotNull { entry ->
                    val pair = entry as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
                    val token = pair.getOrNull(0)?.jsonPrimitive?.content ?: return@mapNotNull null
                    val id = pair.getOrNull(1)?.jsonPrimitive?.int ?: return@mapNotNull null
                    id to token
                }
                .sortedBy { it.first }
                .map { it.second }

            else -> null
        } ?: return@runCatching null
        byId
    }.getOrNull()

    private companion object {
        const val CPT_REPO = "https://huggingface.co/Quien1/manga-ocr-base-CPT-v2-ONNX/resolve/main"
        const val CPT_MIRROR = "https://hf-mirror.com/Quien1/manga-ocr-base-CPT-v2-ONNX/resolve/main"

        val ENCODER_URLS = listOf(
            "$CPT_REPO/onnx/encoder_model_quantized.onnx",
            "$CPT_MIRROR/onnx/encoder_model_quantized.onnx",
        )
        val DECODER_URLS = listOf(
            "$CPT_REPO/onnx/decoder_model_merged_quantized.onnx",
            "$CPT_MIRROR/onnx/decoder_model_merged_quantized.onnx",
        )
        val VOCAB_URLS = listOf(
            "$CPT_REPO/vocab.txt",
            "$CPT_MIRROR/vocab.txt",
        )

        const val MIN_ENCODER_BYTES = 5_000_000L
        const val MIN_DECODER_BYTES = 1_000_000L
    }
}

/** Catalog entry shown in Settings; the actual files are resolved by [MangaOcrStore]. */
object MangaOcrCatalog {
    const val ID = "manga-ocr-base-cpt"
    const val DISPLAY_NAME = "Manga OCR CPT (日文)"
    const val APPROX_BYTES = 120_000_000L
}
