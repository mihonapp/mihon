package eu.kanade.tachiyomi.ui.reader.util

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.VerticalBrightnessGesture
import kotlin.math.abs
import kotlin.math.roundToInt

object ReaderBrightnessGesture {

    const val DEFAULT_EDGE_THRESHOLD_FRACTION = 0.05f
    const val MIN_BRIGHTNESS = -75
    const val MAX_BRIGHTNESS = 100

    /**
     * Checks whether initial touch position [x] falls inside the edge zone for [mode].
     */
    fun isInEdgeZone(
        x: Float,
        width: Float,
        mode: VerticalBrightnessGesture,
        edgeThresholdFraction: Float = DEFAULT_EDGE_THRESHOLD_FRACTION,
    ): Boolean {
        if (width <= 0f) return false
        val edgeWidth = width * edgeThresholdFraction
        return when (mode) {
            VerticalBrightnessGesture.LEFT -> x in 0f..edgeWidth
            VerticalBrightnessGesture.RIGHT -> x in (width - edgeWidth)..width
            VerticalBrightnessGesture.OFF -> false
        }
    }

    /**
     * Determines whether vertical movement [dy] exceeds [touchSlop] and dominates horizontal movement [dx].
     */
    fun isVerticalGesture(dx: Float, dy: Float, touchSlop: Float): Boolean {
        return abs(dy) > touchSlop && abs(dy) > abs(dx) * 2f
    }

    /**
     * Calculates the new brightness value given the initial brightness value,
     * vertical drag distance [deltaYPixels], total screen height [screenHeightPixels],
     * and a sensitivity factor where 100% height = 100 units.
     *
     * Swiping UP (negative [deltaYPixels]) increases brightness.
     * Swiping DOWN (positive [deltaYPixels]) decreases brightness.
     */
    fun calculateBrightnessValue(
        initialBrightness: Int,
        deltaYPixels: Float,
        screenHeightPixels: Float,
        sensitivityScale: Float = 100f,
    ): Int {
        if (screenHeightPixels <= 0f) return initialBrightness
        val change = (-deltaYPixels / screenHeightPixels) * sensitivityScale
        val newBrightness = (initialBrightness + change).roundToInt()
        return newBrightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
    }
}
