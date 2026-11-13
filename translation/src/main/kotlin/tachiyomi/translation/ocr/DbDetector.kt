package tachiyomi.translation.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * DB text detection, the same first stage RapidOCR uses. Output is a probability map; we binarise
 * it, take connected components and expand each box with DB's unclip ratio.
 *
 * Axis aligned boxes are enough here: manga bubbles are near-rectangular and the user taps a region,
 * so rotated quads would add cost without improving the hit target.
 */
class DbDetector(
    private val modelFile: File,
    private val maxSide: Int = 960,
    private val threshold: Float = 0.3f,
    private val minArea: Int = 24,
) : AutoCloseable {
    data class Region(
        val rect: RectF,
        val score: Float,
    )

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy {
        env.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2)
            },
        )
    }
    private val inputName: String by lazy { session.inputNames.first() }

    /** Same gate as [OnnxOcrEngine]: ORT aborts natively on concurrent run/close of one session. */
    private val gate = kotlinx.coroutines.sync.Mutex()

    @Volatile
    private var closed = false

    suspend fun detect(source: Bitmap): List<Region> = gate.withLock {
        if (closed) {
            emptyList()
        } else {
            detectLocked(source)
        }
    }

    private fun detectLocked(source: Bitmap): List<Region> {
        val scale = min(1f, maxSide.toFloat() / max(source.width, source.height))
        val width = ((source.width * scale).roundToInt() / 32 * 32).coerceAtLeast(32)
        val height = ((source.height * scale).roundToInt() / 32 * 32).coerceAtLeast(32)

        val resized = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        resized.getPixels(pixels, 0, width, 0, 0, width, height)
        if (resized !== source) resized.recycle()

        val buffer = FloatArray(3 * width * height)
        val channel = width * height
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            buffer[i] = (r - 0.485f) / 0.229f
            buffer[channel + i] = (g - 0.456f) / 0.224f
            buffer[2 * channel + i] = (b - 0.406f) / 0.225f
        }

        val probabilities = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(buffer),
            longArrayOf(1, 3, height.toLong(), width.toLong()),
        ).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { output ->
                flattenProbability(output[0].value)
            }
        } ?: return emptyList()

        val scaleX = source.width.toFloat() / width
        val scaleY = source.height.toFloat() / height
        return components(probabilities, width, height)
            .mapNotNull { box ->
                val w = (box.right - box.left) * scaleX
                val h = (box.bottom - box.top) * scaleY
                val d = unclip(w, h)
                val rect = RectF(
                    (box.left * scaleX - d).coerceAtLeast(0f),
                    (box.top * scaleY - d).coerceAtLeast(0f),
                    (box.right * scaleX + d).coerceAtMost(source.width.toFloat()),
                    (box.bottom * scaleY + d).coerceAtMost(source.height.toFloat()),
                )
                if (rect.width() < 6f || rect.height() < 6f) return@mapNotNull null
                if (max(rect.width(), rect.height()) / min(rect.width(), rect.height()) > 25f) return@mapNotNull null
                Region(rect, box.score)
            }
            .sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
    }

    private class Box(val left: Int, val top: Int, val right: Int, val bottom: Int, val score: Float)

    private fun components(prob: FloatArray, width: Int, height: Int): List<Box> {
        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        val boxes = ArrayList<Box>()

        for (start in prob.indices) {
            if (visited[start] || prob[start] < threshold) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            var count = 0
            var scoreSum = 0f

            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
                count++
                scoreSum += prob[index]

                if (x > 0) tail = visit(index - 1, prob, visited, queue, tail)
                if (x < width - 1) tail = visit(index + 1, prob, visited, queue, tail)
                if (y > 0) tail = visit(index - width, prob, visited, queue, tail)
                if (y < height - 1) tail = visit(index + width, prob, visited, queue, tail)
            }

            if (count >= minArea) {
                boxes += Box(left, top, right + 1, bottom + 1, scoreSum / count)
            }
        }
        return boxes
    }

    private fun visit(
        index: Int,
        prob: FloatArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
    ): Int {
        if (visited[index] || prob[index] < threshold) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    /** DB's unclip: shrink-free expansion proportional to the box's own area/perimeter. */
    private fun unclip(width: Float, height: Float): Float {
        val area = width * height
        val perimeter = 2f * (width + height)
        if (perimeter <= 0f) return 0f
        return (area / perimeter * 1.5f).coerceIn(1f, 12f)
    }

    /** Unwraps `[1, 1, H, W]` (or `[H, W]`) into a row-major probability array. */
    private fun flattenProbability(value: Any?): FloatArray? {
        var node: Any? = value
        while (node is Array<*>) {
            val first = node.firstOrNull() ?: return null
            if (first is FloatArray) {
                @Suppress("UNCHECKED_CAST")
                val rows = node as Array<FloatArray>
                val height = rows.size
                val width = rows.firstOrNull()?.size ?: return null
                val out = FloatArray(width * height)
                for (y in 0 until height) {
                    System.arraycopy(rows[y], 0, out, y * width, width)
                }
                return out
            }
            node = first
        }
        return null
    }

    override fun close() {
        // Skipped while a detection holds the gate, mirroring [OnnxOcrEngine.close].
        if (!gate.tryLock()) return
        try {
            if (closed) return
            closed = true
            runCatching { session.close() }
        } finally {
            gate.unlock()
        }
    }
}
