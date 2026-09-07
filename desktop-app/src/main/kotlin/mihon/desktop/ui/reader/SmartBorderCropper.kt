package mihon.desktop.ui.reader

import java.awt.image.BufferedImage
import kotlin.math.max

object SmartBorderCropper {

    data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

    private enum class BorderType {
        WHITE,
        BLACK,
        NONE,
    }

    /**
     * Detects borders and crops the image if significant borders (>= 4px) are found.
     * Guaranteed to crop at most [maxCropPercent] (default 20%) from each edge.
     */
    fun crop(image: BufferedImage, maxCropPercent: Float = 0.20f): BufferedImage {
        val rect = detectCropRect(image, maxCropPercent) ?: return image
        return image.getSubimage(rect.x, rect.y, rect.width, rect.height)
    }

    fun detectCropRect(image: BufferedImage, maxCropPercent: Float = 0.20f): CropRect? {
        val width = image.width
        val height = image.height
        if (width < 30 || height < 30) return null

        val borderType = detectBorderType(image)
        if (borderType == BorderType.NONE) return null

        val maxCropX = (width * maxCropPercent).toInt().coerceAtLeast(1)
        val maxCropY = (height * maxCropPercent).toInt().coerceAtLeast(1)

        val xStep = max(1, width / 40)
        val yStep = max(1, height / 40)

        // 1. Top
        var cropTop = 0
        for (y in 0 until maxCropY) {
            var allBorder = true
            for (x in 0 until width step xStep) {
                if (!isBorderPixel(image.getRGB(x, y), borderType)) {
                    allBorder = false
                    break
                }
            }
            if (!allBorder) {
                cropTop = y
                break
            }
            cropTop = y + 1
        }

        // 2. Bottom
        var cropBottom = 0
        for (y in height - 1 downTo height - maxCropY) {
            var allBorder = true
            for (x in 0 until width step xStep) {
                if (!isBorderPixel(image.getRGB(x, y), borderType)) {
                    allBorder = false
                    break
                }
            }
            if (!allBorder) {
                cropBottom = height - 1 - y
                break
            }
            cropBottom = height - y
        }

        // 3. Left
        var cropLeft = 0
        for (x in 0 until maxCropX) {
            var allBorder = true
            for (y in 0 until height step yStep) {
                if (!isBorderPixel(image.getRGB(x, y), borderType)) {
                    allBorder = false
                    break
                }
            }
            if (!allBorder) {
                cropLeft = x
                break
            }
            cropLeft = x + 1
        }

        // 4. Right
        var cropRight = 0
        for (x in width - 1 downTo width - maxCropX) {
            var allBorder = true
            for (y in 0 until height step yStep) {
                if (!isBorderPixel(image.getRGB(x, y), borderType)) {
                    allBorder = false
                    break
                }
            }
            if (!allBorder) {
                cropRight = width - 1 - x
                break
            }
            cropRight = width - x
        }

        // Avoid cropping truly blank pages where all edges hit max limit and center has no content
        if (cropTop >= maxCropY && cropBottom >= maxCropY && cropLeft >= maxCropX && cropRight >= maxCropX) {
            val hasCenterContent = (1..3).any { i ->
                (1..3).any { j ->
                    !isBorderPixel(image.getRGB(width * i / 4, height * j / 4), borderType)
                }
            }
            if (!hasCenterContent) return null
        }

        // Only crop if at least one edge has a meaningful margin (>= 4px)
        if (cropTop < 4 && cropBottom < 4 && cropLeft < 4 && cropRight < 4) {
            return null
        }

        val targetWidth = width - cropLeft - cropRight
        val targetHeight = height - cropTop - cropBottom
        if (targetWidth < 20 || targetHeight < 20) return null

        return CropRect(cropLeft, cropTop, targetWidth, targetHeight)
    }

    private fun detectBorderType(image: BufferedImage): BorderType {
        val w = image.width
        val h = image.height

        val corners = listOf(
            image.getRGB(0, 0),
            image.getRGB(w - 1, 0),
            image.getRGB(0, h - 1),
            image.getRGB(w - 1, h - 1),
        )

        var whiteCount = 0
        var blackCount = 0

        for (color in corners) {
            val a = (color ushr 24) and 0xff
            val r = (color ushr 16) and 0xff
            val g = (color ushr 8) and 0xff
            val b = color and 0xff

            if (a < 50 || (r > 230 && g > 230 && b > 230)) {
                whiteCount++
            } else if (r < 30 && g < 30 && b < 30) {
                blackCount++
            }
        }

        return when {
            whiteCount >= 3 -> BorderType.WHITE
            blackCount >= 3 -> BorderType.BLACK
            else -> BorderType.NONE
        }
    }

    private fun isBorderPixel(color: Int, borderType: BorderType): Boolean {
        val a = (color ushr 24) and 0xff
        if (a < 50) return true // transparent counts as border

        val r = (color ushr 16) and 0xff
        val g = (color ushr 8) and 0xff
        val b = color and 0xff

        return when (borderType) {
            BorderType.WHITE -> r >= 230 && g >= 230 && b >= 230
            BorderType.BLACK -> r <= 30 && g <= 30 && b <= 30
            BorderType.NONE -> false
        }
    }
}
