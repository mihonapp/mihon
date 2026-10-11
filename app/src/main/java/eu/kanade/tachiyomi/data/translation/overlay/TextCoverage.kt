package eu.kanade.tachiyomi.data.translation.overlay

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** OCR text and its tight pixel bounds in the image that was independently inspected. */
data class DetectedTextRegion(val bounds: PixelRect, val text: String)

/** Compares independent OCR text regions with Gemini's detections and identifies likely omissions. */
object TextCoverage {

    fun missingRegions(
        regions: List<DetectedTextRegion>,
        boxes: List<TextBox>,
        imageWidth: Int,
        imageHeight: Int,
        translateSfx: Boolean,
        minTranscriptionCoverage: Float = MIN_TRANSCRIPTION_COVERAGE,
    ): List<DetectedTextRegion> {
        require(imageWidth > 0 && imageHeight > 0)
        return regions.filter { region ->
            val expectedTokens = tokens(region.text)
            if (expectedTokens.isEmpty() || region.bounds.isEmpty) return@filter false

            val overlapping = boxes.filter { box ->
                coverage(region.bounds, box.toPixelRect(imageWidth, imageHeight)) >= MIN_REGION_COVERAGE
            }
            if (!translateSfx) {
                val sfxTokens = overlapping.filter { it.kind == BoxKind.SFX }
                    .flatMap { tokens(it.sourceText) }
                    .toSet()
                val coveredByDisabledSfx = expectedTokens.count { it in sfxTokens }.toFloat() /
                    expectedTokens.size >= MIN_TRANSCRIPTION_COVERAGE
                if (coveredByDisabledSfx) return@filter false
            }
            val translatedOverlapping = overlapping.filter { it.kind != BoxKind.SFX || translateSfx }
            if (translatedOverlapping.isEmpty()) return@filter true

            val sourceTokens = translatedOverlapping.flatMap { tokens(it.sourceText) }.toSet()
            if (sourceTokens.isEmpty()) return@filter true
            expectedTokens.count { it in sourceTokens }.toFloat() / expectedTokens.size < minTranscriptionCoverage
        }
    }

    /**
     * Constrains a recovery box to the independently detected text region plus a small glyph-outline margin,
     * then maps it from the recovery crop back to the inspected upload. The caller maps the result to the page.
     * This prevents a recovery response from expanding its erasure area into nearby artwork or faces.
     */
    fun clampRecoveryBox(
        box: TextBox,
        recoveryCrop: PixelRect,
        targetRegion: DetectedTextRegion,
        imageWidth: Int,
        imageHeight: Int,
    ): TextBox? {
        require(imageWidth > 0 && imageHeight > 0 && recoveryCrop.width > 0 && recoveryCrop.height > 0)
        val recovered = box.toPixelRect(recoveryCrop.width, recoveryCrop.height)
        val translated = PixelRect(
            recovered.left + recoveryCrop.left,
            recovered.top + recoveryCrop.top,
            recovered.right + recoveryCrop.left,
            recovered.bottom + recoveryCrop.top,
        )
        val margin = max(MIN_GLYPH_MARGIN, (targetRegion.bounds.height * GLYPH_MARGIN_RATIO).roundToInt())
        val allowed = targetRegion.bounds.expandedWithin(
            margin,
            margin,
            PixelRect(0, 0, imageWidth, imageHeight),
        )
        val safe = PixelRect(
            max(translated.left, allowed.left),
            max(translated.top, allowed.top),
            min(translated.right, allowed.right),
            min(translated.bottom, allowed.bottom),
        )
        if (safe.width < MIN_RECOVERED_BOX_SIDE || safe.height < MIN_RECOVERED_BOX_SIDE) return null

        val localBox = TextBox(
            yMin = (safe.top.toFloat() / imageHeight * TextBox.GRID).roundToInt().coerceIn(0, 999),
            xMin = (safe.left.toFloat() / imageWidth * TextBox.GRID).roundToInt().coerceIn(0, 999),
            yMax = (safe.bottom.toFloat() / imageHeight * TextBox.GRID).roundToInt().coerceIn(1, 1000),
            xMax = (safe.right.toFloat() / imageWidth * TextBox.GRID).roundToInt().coerceIn(1, 1000),
            kind = box.kind,
            text = box.text,
            sourceText = box.sourceText,
        )
        return localBox
    }

    private fun coverage(region: PixelRect, box: PixelRect): Float {
        val intersectionWidth = max(0, min(region.right, box.right) - max(region.left, box.left))
        val intersectionHeight = max(0, min(region.bottom, box.bottom) - max(region.top, box.top))
        val intersection = intersectionWidth.toLong() * intersectionHeight
        return if (region.area <= 0L) 0f else intersection.toFloat() / region.area
    }

    private fun tokens(text: String): List<String> = text.lowercase()
        .split(WORD_SEPARATOR)
        .filter(String::isNotBlank)

    private const val MIN_REGION_COVERAGE = 0.1f
    private const val MIN_TRANSCRIPTION_COVERAGE = 0.9f
    private const val MIN_GLYPH_MARGIN = 3
    private const val GLYPH_MARGIN_RATIO = 0.2f
    private const val MIN_RECOVERED_BOX_SIDE = 2
    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
