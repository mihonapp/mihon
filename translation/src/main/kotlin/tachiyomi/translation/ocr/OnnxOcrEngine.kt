package tachiyomi.translation.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CTC recognition with a PP-OCR style ONNX model: input `[1, 3, 48, W]` normalized to -1..1,
 * output `[1, T, C]` where index 0 is the CTC blank and the remaining indices map to [charset].
 */
class OnnxOcrEngine(
    private val modelFile: File,
    private val charset: List<String>,
    private val inputHeight: Int = 48,
    private val maxWidth: Int = 1280,
) : OcrEngine, AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy {
        env.createSession(modelFile.absolutePath, OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(2)
        })
    }
    private val inputName: String by lazy { session.inputNames.first() }

    /**
     * ORT aborts natively (Scudo "invalid chunk state" during `OrtSession_run`) when the same
     * session is run or closed from two threads at once — an auto scan and a hand-drawn box can
     * both land here concurrently. Every inference therefore holds this gate.
     */
    private val gate = Mutex()

    @Volatile
    private var closed = false

    override suspend fun recognize(crop: Bitmap, vertical: Boolean): OcrResult = withContext(Dispatchers.Default) {
        gate.withLock {
            if (closed) return@withLock OcrResult(emptyList())
            val rotated = if (vertical) rotateToHorizontal(crop) else crop
            val prepared = OcrImagePreprocessor.forCtc(rotated)
            try {
                runInference(prepared)
            } catch (e: Throwable) {
                android.util.Log.w("PaddleOcr", "recognition failed", e)
                OcrResult(emptyList())
            } finally {
                if (prepared !== rotated) prepared.recycle()
                if (rotated !== crop) rotated.recycle()
            }
        }
    }

    private fun runInference(bitmap: Bitmap): OcrResult {
        val targetHeight = inputHeight
        val scale = targetHeight.toFloat() / bitmap.height
        val targetWidth = (bitmap.width * scale).roundToInt().coerceIn(16, maxWidth)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        try {
            val pixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

            val channelSize = targetWidth * targetHeight
            val buffer = FloatArray(3 * channelSize)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                buffer[i] = r / 127.5f - 1f
                buffer[channelSize + i] = g / 127.5f - 1f
                buffer[2 * channelSize + i] = b / 127.5f - 1f
            }

            return OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(buffer),
                longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()),
            ).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { output ->
                    decode(output[0].value)
                }
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(value: Any): OcrResult {
        val steps: Array<FloatArray> = when (value) {
            is Array<*> -> {
                val first = value.firstOrNull()
                when (first) {
                    is Array<*> -> first as Array<FloatArray>
                    is FloatArray -> value as Array<FloatArray>
                    else -> return OcrResult(emptyList())
                }
            }
            else -> return OcrResult(emptyList())
        }

        val builder = StringBuilder()
        var confidenceSum = 0.0
        var confidenceCount = 0
        var lastIndex = -1
        // PP-OCR appends a space class right after the dictionary when trained with
        // `use_space_char`, so the model has two more classes than the dictionary has entries.
        // The English and Korean dictionaries rely on it; dropping it welded words together.
        val classCount = steps.firstOrNull { it.isNotEmpty() }?.size ?: 0
        val spaceIndex = if (classCount == charset.size + 2) charset.size else -1

        for (step in steps) {
            if (step.isEmpty()) continue
            var best = 0
            var bestScore = step[0]
            for (i in 1 until step.size) {
                if (step[i] > bestScore) {
                    bestScore = step[i]
                    best = i
                }
            }
            if (best != 0 && best != lastIndex) {
                val index = best - 1
                val char = when {
                    index in charset.indices -> charset[index]
                    index == spaceIndex -> " "
                    else -> null
                }
                if (!char.isNullOrEmpty()) builder.append(char)
            }
            if (best != 0) {
                confidenceSum += softmaxMax(step)
                confidenceCount++
            }
            lastIndex = best
        }

        val text = builder.toString().trim()
        if (text.isEmpty()) return OcrResult(emptyList())
        val confidence = if (confidenceCount == 0) 0f else (confidenceSum / confidenceCount).toFloat()
        return OcrResult(listOf(OcrLine(text, confidence)))
    }

    private fun softmaxMax(logits: FloatArray): Double {
        var maxLogit = logits[0]
        for (v in logits) if (v > maxLogit) maxLogit = v
        var sum = 0.0
        for (v in logits) sum += exp((v - maxLogit).toDouble())
        return 1.0 / sum
    }

    /** PP-OCR trains on horizontal lines, so a vertical column is rotated before inference. */
    private fun rotateToHorizontal(source: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun close() {
        // Never tear the session down under a running inference: that is exactly the native abort
        // above. If the gate is taken the close is skipped and the session lives until GC.
        if (!gate.tryLock()) return
        try {
            if (closed) return
            closed = true
            runCatching { session.close() }
        } finally {
            gate.unlock()
        }
    }

    companion object {
        /** Loads a dictionary file (one token per line; PP-OCR keys files are UTF-8). */
        fun loadCharset(file: File): List<String> = runCatching {
            file.readLines(Charsets.UTF_8)
                .map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() }
        }.getOrElse { emptyList() }

        fun pickMaxSteps(steps: Array<FloatArray>, chars: Int): Int = max(1, steps.size * chars)
    }
}
