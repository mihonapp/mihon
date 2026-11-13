package tachiyomi.translation.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Manga OCR (`kha-white/manga-ocr`) exported to ONNX.
 *
 * The model is a DeiT image encoder plus a small character-level BERT decoder. The export used
 * here has no KV cache, so decoding feeds the whole prefix back every step and is greedy. It is
 * Japanese-only by design, and slower than the Paddle recogniser per crop, but it handles manga
 * lettering (vertical columns, furigana, outlines) noticeably better.
 */
class MangaOcrEngine(
    encoderFile: File,
    private val decoderFile: File,
    vocabFile: File,
    private val maxLength: Int = 96,
) : OcrEngine, AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(2)
    }
    private val encoder: OrtSession = env.createSession(encoderFile.absolutePath, options)
    private val decoder: OrtSession = env.createSession(decoderFile.absolutePath, options)

    private val vocab: List<String> = runCatching {
        vocabFile.readLines().map { it.trimEnd('\r', '\n') }
    }.getOrDefault(emptyList())

    // The CPT model replaced the vocabulary, so the special token ids are looked up rather than
    // assumed; they fall back to the BERT defaults if the file does not name them.
    private val startToken: Long = vocab.indexOf("[CLS]").takeIf { it >= 0 }?.toLong() ?: 2L
    private val endToken: Long = vocab.indexOf("[SEP]").takeIf { it >= 0 }?.toLong() ?: 3L

    private val encoderInput: String = encoder.inputNames.first()
    private val decoderInputs: Set<String> = decoder.inputNames

    /** No concurrent run/close on the sessions: ORT aborts natively when that happens. */
    private val gate = Mutex()

    @Volatile
    private var closed = false

    /** Length of the encoder sequence, needed for `encoder_attention_mask`. */
    private val encoderLength: Int by lazy {
        val info = runCatching { encoder.outputInfo.values.first().getInfo() as? TensorInfo }.getOrNull()
        val dims = info?.shape
        if (dims != null && dims.size >= 2 && dims[1] > 0) dims[1].toInt() else 197
    }

    override suspend fun recognize(crop: Bitmap, vertical: Boolean): OcrResult = withContext(Dispatchers.Default) {
        if (vocab.isEmpty()) return@withContext OcrResult(emptyList())
        // Same native hazard as the CTC engine: no concurrent run/close on these sessions.
        gate.withLock {
            if (closed) return@withLock OcrResult(emptyList())
            val pixels = preprocess(crop)
            try {
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(pixels),
                    longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong()),
                ).use { image ->
                    encoder.run(mapOf(encoderInput to image)).use { output ->
                        val hidden = output.get(0) as OnnxTensor
                        val text = decode(hidden)
                        if (text.isBlank()) {
                            android.util.Log.w("MangaOcr", "decode produced no text")
                        }
                        if (text.isBlank()) OcrResult(emptyList()) else OcrResult(listOf(OcrLine(text, 0.9f)))
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("MangaOcr", "decode failed", e)
                OcrResult(emptyList())
            }
        }
    }

    /** Grayscale, square resize, normalise to mean 0.5 / std 0.5 as the original pipeline does. */
    private fun preprocess(source: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(source, IMAGE_SIZE, IMAGE_SIZE, true)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        if (scaled !== source) scaled.recycle()

        val plane = IMAGE_SIZE * IMAGE_SIZE
        val buffer = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000f / 255f
            val value = (luma - 0.5f) / 0.5f
            buffer[i] = value
            buffer[plane + i] = value
            buffer[2 * plane + i] = value
        }
        return buffer
    }

    private fun decode(hidden: OnnxTensor): String {
        val ids = ArrayList<Long>(maxLength).apply { add(startToken) }
        val text = StringBuilder()

        repeat(maxLength) {
            val next = nextToken(ids, hidden)
            if (next == endToken) return text.toString()
            ids.add(next)
            val token = vocab.getOrNull(next.toInt()).orEmpty()
            if (token.isNotEmpty() && !token.startsWith("[") ) {
                text.append(token)
            }
        }
        return text.toString()
    }

    private fun nextToken(ids: List<Long>, hidden: OnnxTensor): Long {
        val length = ids.size.toLong()
        val temps = ArrayList<OnnxTensor>(decoderInputs.size + 2)
        try {
            val idsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(ids.toLongArray()),
                longArrayOf(1, length),
            ).also(temps::add)
            val mask = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(ids.size) { 1L }),
                longArrayOf(1, length),
            ).also(temps::add)
            val encoderMask = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(encoderLength) { 1L }),
                longArrayOf(1, encoderLength.toLong()),
            ).also(temps::add)
            val noCache = OnnxTensor.createTensor(
                env,
                java.nio.ByteBuffer.wrap(byteArrayOf(0)),
                longArrayOf(1),
                OnnxJavaType.BOOL,
            ).also(temps::add)

            val inputs = HashMap<String, OnnxTensor>(decoderInputs.size)
            for (name in decoderInputs) {
                inputs[name] = when {
                    name.contains("encoder_hidden") -> hidden
                    name.startsWith("past_key_values") -> emptyPastTensor(name).also(temps::add)
                    name.contains("encoder_attention") -> encoderMask
                    name.contains("attention_mask") || name.contains("token_type") -> mask
                    name.contains("use_cache") -> noCache
                    else -> idsTensor
                }
            }

            decoder.run(inputs).use { output ->
                // Some exports name the head, some do not; logits is always the first output.
                val named = runCatching { output.get("logits").orElse(null) }.getOrNull()
                val logits = named ?: output.get(0)
                return lastStepArgMax(logits.value)
            }
        } finally {
            temps.forEach { runCatching { it.close() } }
        }
    }

    /** A zero-length past tensor with the shape the graph declares for this input. */
    private fun emptyPastTensor(name: String): OnnxTensor {
        // inputInfo hands back NodeInfo wrappers; the TensorInfo (with the shape) sits behind
        // getInfo(). Missing that step left every past tensor at rank 2 and killed the decode.
        val info = runCatching { decoder.inputInfo[name]?.getInfo() as? TensorInfo }.getOrNull()
        val dims = info?.shape?.mapIndexed { index, dim ->
            when {
                dim > 0 -> dim
                index == 0 -> 1L
                else -> 0L
            }
        }?.toLongArray() ?: longArrayOf(1, 12, 0, 64)
        return OnnxTensor.createTensor(env, FloatBuffer.allocate(0), dims)
    }

    /** Logits come as `[1, sequence, vocab]`; only the last step matters for greedy decoding. */
    private fun lastStepArgMax(value: Any?): Long {
        var node: Any? = value
        while (node is Array<*> && node.isNotEmpty() && node[0] is Array<*>) {
            node = node[0]
        }
        val steps = node as? Array<*> ?: return endToken
        val row = steps.lastOrNull() as? FloatArray ?: return endToken
        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in row.indices) {
            if (row[i] > bestValue) {
                bestValue = row[i]
                best = i
            }
        }
        return best.toLong()
    }

    override fun close() {
        // Skipped while decoding holds the gate, mirroring the CTC engine's close.
        if (!gate.tryLock()) return
        try {
            if (closed) return
            closed = true
            runCatching { encoder.close() }
            runCatching { decoder.close() }
        } finally {
            gate.unlock()
        }
    }

    private companion object {
        const val IMAGE_SIZE = 224
    }
}
