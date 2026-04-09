package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

internal abstract class BaseColorScheme {

    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme

    // Cannot be pure black as there's content scrolling behind it
    // https://m3.material.io/components/navigation-bar/guidelines#90615a71-607e-485e-9e09-778bfc080563
    private val surfaceContainer = Color(0xFF0C0C0C)
    private val surfaceContainerHigh = Color(0xFF131313)
    private val surfaceContainerHighest = Color(0xFF1B1B1B)

    fun getColorScheme(
        isDark: Boolean,
        isAmoled: Boolean,
        isEInk: Boolean = false,
        overrideDarkSurfaceContainers: Boolean,
    ): ColorScheme {
        val baseScheme = if (isDark) darkScheme else lightScheme

        // Apply e-ink adjustments first (before AMOLED, since they interact)
        val scheme = if (isEInk) applyEInkAdjustments(baseScheme, isDark) else baseScheme

        if (!isDark) return scheme

        if (!isAmoled) return scheme

        val amoledScheme = scheme.copy(
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
        )

        if (!overrideDarkSurfaceContainers) return amoledScheme

        return amoledScheme.copy(
            surfaceVariant = surfaceContainer, // Navigation bar background (ThemePrefWidget)
            surfaceContainerLowest = surfaceContainer,
            surfaceContainerLow = surfaceContainer,
            surfaceContainer = surfaceContainer, // Navigation bar background
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }

    /**
     * Applies e-ink optimizations to a color scheme.
     *
     * Color e-ink displays (Boox, Kaleido, Gallery 3, etc.) have:
     * - Lower contrast ratios than LCD/OLED (~10:1 vs 1000:1+)
     * - Limited color gamut (colors appear muted/washed out)
     * - No backlight (ambient light only)
     *
     * This adjustment:
     * 1. Widens the brightness gap between surface elevation levels so modals
     *    and popups are clearly distinguishable from the background
     * 2. Strengthens outline colors so borders are visible
     * 3. Boosts saturation of accent colors for the limited gamut
     */
    private fun applyEInkAdjustments(scheme: ColorScheme, isDark: Boolean): ColorScheme {
        return if (isDark) applyEInkDark(scheme) else applyEInkLight(scheme)
    }

    private fun applyEInkDark(scheme: ColorScheme): ColorScheme {
        val bg = scheme.background

        return scheme.copy(
            // Boost surface containers: each level is pushed further from background
            // Original themes typically have ~5-10 brightness units between levels;
            // e-ink needs ~20-30 to be distinguishable
            surfaceContainerLowest = lighten(bg, 0.08f),
            surfaceContainerLow = lighten(bg, 0.12f),
            surfaceContainer = lighten(bg, 0.18f),
            surfaceContainerHigh = lighten(bg, 0.24f),
            surfaceContainerHighest = lighten(bg, 0.30f),
            surfaceVariant = lighten(bg, 0.18f),

            // Strengthen outlines so borders are clearly visible
            outline = lighten(scheme.outline, 0.25f),
            outlineVariant = lighten(scheme.outlineVariant, 0.20f),

            // Boost accent saturation for limited e-ink color gamut
            primary = saturate(scheme.primary, 1.3f),
            secondary = saturate(scheme.secondary, 1.3f),
            tertiary = saturate(scheme.tertiary, 1.3f),
        )
    }

    private fun applyEInkLight(scheme: ColorScheme): ColorScheme {
        val bg = scheme.background

        return scheme.copy(
            // Darken surface containers away from the white background
            surfaceContainerLowest = darken(bg, 0.20f),
            surfaceContainerLow = darken(bg, 0.14f),
            surfaceContainer = darken(bg, 0.08f),
            surfaceContainerHigh = darken(bg, 0.04f),
            surfaceContainerHighest = darken(bg, 0.02f),
            surfaceVariant = darken(bg, 0.08f),

            // Strengthen outlines
            outline = darken(scheme.outline, 0.20f),
            outlineVariant = darken(scheme.outlineVariant, 0.15f),

            // Boost accent saturation
            primary = saturate(scheme.primary, 1.3f),
            secondary = saturate(scheme.secondary, 1.3f),
            tertiary = saturate(scheme.tertiary, 1.3f),
        )
    }

    companion object {
        /**
         * Lighten a color by blending it toward white.
         * @param fraction 0.0 = no change, 1.0 = pure white
         */
        internal fun lighten(color: Color, fraction: Float): Color {
            val argb = color.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return Color(
                red = min(255, r + ((255 - r) * fraction).toInt()),
                green = min(255, g + ((255 - g) * fraction).toInt()),
                blue = min(255, b + ((255 - b) * fraction).toInt()),
            )
        }

        /**
         * Darken a color by blending it toward black.
         * @param fraction 0.0 = no change, 1.0 = pure black
         */
        internal fun darken(color: Color, fraction: Float): Color {
            val argb = color.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return Color(
                red = max(0, (r * (1f - fraction)).toInt()),
                green = max(0, (g * (1f - fraction)).toInt()),
                blue = max(0, (b * (1f - fraction)).toInt()),
            )
        }

        /**
         * Boost saturation of a color while preserving its luminance.
         * Works by converting to HSL, multiplying saturation, and converting back.
         * @param factor > 1.0 increases saturation, < 1.0 decreases it
         */
        internal fun saturate(color: Color, factor: Float): Color {
            val argb = color.toArgb()
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f

            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val l = (maxC + minC) / 2f

            if (maxC == minC) return color // achromatic, nothing to saturate

            val d = maxC - minC
            val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
            val h = when {
                maxC == r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
                maxC == g -> ((b - r) / d + 2f) / 6f
                else -> ((r - g) / d + 4f) / 6f
            }

            val newS = min(1f, s * factor)
            return hslToColor(h, newS, l)
        }

        private fun hslToColor(h: Float, s: Float, l: Float): Color {
            val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
            val x = c * (1f - kotlin.math.abs((h * 6f) % 2f - 1f))
            val m = l - c / 2f
            val (r, g, b) = when {
                h < 1f / 6f -> Triple(c, x, 0f)
                h < 2f / 6f -> Triple(x, c, 0f)
                h < 3f / 6f -> Triple(0f, c, x)
                h < 4f / 6f -> Triple(0f, x, c)
                h < 5f / 6f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color(
                red = min(255, ((r + m) * 255f).toInt()),
                green = min(255, ((g + m) * 255f).toInt()),
                blue = min(255, ((b + m) * 255f).toInt()),
            )
        }
    }
}
