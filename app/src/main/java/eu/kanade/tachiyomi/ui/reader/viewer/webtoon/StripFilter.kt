package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Stack

/**
 * Strip/Border removal filter for reading content, and is dynamically determined based on the viewer configuration.
 * Removes white strips and replaces them with black or a customizable color.
 *
 * (Heavily inspired by various fully open source "trim" or "border detection" algorithms found on github.)
 */
object StripFilter {

    enum class ReadingMode {
        /** Long-strip content (webtoon, vertical) - detects left/right margins */
        WEBTOON,
        /** Paged content (manga) - detects all four sides */
        MANGA_PAGED,
    }

    data class Config(
        /** Threshold for detecting white/light colored pixels (0-255) */
        val whiteThreshold: Int = 230,
        /** Color to replace detected borders with (argb) */
        val replacementColor: Int = Color.parseColor("#121212"),
        /** Width percentage threshold for detecting gutters (0.0-1.0) */
        val gutterWidthPercentage: Float = 0.90f,
        /** Height percentage threshold for tall margins (0.0-1.0) */
        val tallMarginHeightPercentage: Float = 0.30f,
        /** Area percentage threshold for large chunks (0.0-1.0) */
        val bigChunkAreaPercentage: Float = 0.20f,
        /** Reading mode for margin detection algorithm selection */
        val readingMode: ReadingMode = ReadingMode.WEBTOON,
    )

    fun process(bitmap: Bitmap, config: Config = Config()): Bitmap {
        return when (config.readingMode) {
            ReadingMode.WEBTOON -> processWebtoon(bitmap, config)
            ReadingMode.MANGA_PAGED -> processMangaPaged(bitmap, config)
        }
    }

    /**
     * Webtoon-specific processing: Detects left and right margins/gutters.
     */
    private fun processWebtoon(bitmap: Bitmap, config: Config): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val mask = BooleanArray(w * h)

        // Pass 1: Left Curtain
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val col = pixels[idx]
                if (isColorLightEnough(col, config.whiteThreshold)) {
                    mask[idx] = true
                } else {
                    break
                }
            }
        }

        // Pass 2: Right Curtain
        for (y in 0 until h) {
            for (x in w - 1 downTo 0) {
                val idx = y * w + x
                if (mask[idx]) continue

                val col = pixels[idx]
                if (isColorLightEnough(col, config.whiteThreshold)) {
                    mask[idx] = true
                } else {
                    break
                }
            }
        }

        return applyConnectedComponentsAndCleanup(bitmap, mask, pixels, config)
    }

    /**
     * Manga-specific processing: Detects margins on all four sides.
     */
    private fun processMangaPaged(bitmap: Bitmap, config: Config): Bitmap {

        // This is very unoptimal, works but it's a complete hit or miss, could be improved a lot

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val mask = BooleanArray(w * h)

        // Pass 1: Left Curtain
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val col = pixels[idx]
                if (isColorLightEnough(col, config.whiteThreshold)) {
                    mask[idx] = true
                } else {
                    break
                }
            }
        }

        // Pass 2: Right Curtain
        for (y in 0 until h) {
            for (x in w - 1 downTo 0) {
                val idx = y * w + x
                if (mask[idx]) continue

                val col = pixels[idx]
                if (isColorLightEnough(col, config.whiteThreshold)) {
                    mask[idx] = true
                } else {
                    break
                }
            }
        }

        // Pass 3: Top Margin
        for (x in 0 until w) {
            for (y in 0 until h) {
                val idx = y * w + x
                if (mask[idx]) continue

                val col = pixels[idx]
                if (isColorLightEnough(col, config.whiteThreshold)) {
                    mask[idx] = true
                } else {
                    break
                }
            }
        }

        // Pass 4: Bottom Margin
        for (x in 0 until w) {
            for (y in h - 1 downTo 0) {
                val idx = y * w + x
                if (mask[idx]) continue

                val col = pixels[idx]
                if (isColorLightEnough(col, config.whiteThreshold)) {
                    mask[idx] = true
                } else {
                    break
                }
            }
        }

        return applyConnectedComponentsAndCleanup(bitmap, mask, pixels, config)
    }

    /**
     * Uses connected components analysis to filter noise, and identify significant borders
     */
    private fun applyConnectedComponentsAndCleanup(
        bitmap: Bitmap,
        mask: BooleanArray,
        pixels: IntArray,
        config: Config,
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val finalMask = BooleanArray(w * h)
        val visited = BooleanArray(w * h)
        val stack = Stack<Int>()

        for (i in 0 until w * h) {
            if (mask[i] && !visited[i]) {
                stack.push(i)
                val componentPixels = ArrayList<Int>()
                var minX = w
                var maxX = 0
                var minY = h
                var maxY = 0

                while (stack.isNotEmpty()) {
                    val cIdx = stack.pop()
                    if (visited[cIdx]) continue
                    visited[cIdx] = true

                    if (!mask[cIdx]) continue

                    componentPixels.add(cIdx)
                    val cx = cIdx % w
                    val cy = cIdx / w

                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    // Neighbors
                    if (cx > 0 && mask[cIdx - 1] && !visited[cIdx - 1]) stack.push(cIdx - 1)
                    if (cx < w - 1 && mask[cIdx + 1] && !visited[cIdx + 1]) stack.push(cIdx + 1)
                    if (cy > 0 && mask[cIdx - w] && !visited[cIdx - w]) stack.push(cIdx - w)
                    if (cy < h - 1 && mask[cIdx + w] && !visited[cIdx + w]) stack.push(cIdx + w)
                }

                val compWidth = maxX - minX
                val compHeight = maxY - minY
                val pixelCount = componentPixels.size

                val isGutter = compWidth > (w * config.gutterWidthPercentage)
                val isTallMargin = compHeight > (h * config.tallMarginHeightPercentage)
                val isBigChunk = pixelCount > (w * h * config.bigChunkAreaPercentage)

                if (isGutter || isTallMargin || isBigChunk) {
                    for (pIdx in componentPixels) {
                        finalMask[pIdx] = true
                    }
                }
            }
        }

        for (i in 0 until w * h) {
            if (finalMask[i]) {
                pixels[i] = config.replacementColor
            }
        }

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun isColorLightEnough(color: Int, threshold: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r >= threshold && g >= threshold && b >= threshold
    }
}
