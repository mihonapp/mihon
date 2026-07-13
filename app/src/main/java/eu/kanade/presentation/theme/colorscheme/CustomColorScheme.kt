package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal class CustomColorScheme(
    private val accent: Color,
    private val on: Color,
    private val surfaceBg: Color,
) : BaseColorScheme() {

    override val darkScheme = generate(isDark = true)
    override val lightScheme = generate(isDark = false)

    private fun generate(isDark: Boolean): ColorScheme {
        val other = if (isDark) Color.Black else Color.White
        val otherOn = if (isDark) Color.White else Color.Black

        return ColorScheme(
            primary = accent,
            onPrimary = on,
            primaryContainer = accent,
            onPrimaryContainer = on,
            inversePrimary = other,

            secondary = accent,
            onSecondary = other,
            secondaryContainer = accent,
            onSecondaryContainer = on,

            tertiary = on,
            onTertiary = other,
            tertiaryContainer = other,
            onTertiaryContainer = otherOn,

            background = surfaceBg,
            onBackground = on,

            surface = surfaceBg,
            onSurface = on,
            surfaceVariant = surfaceBg,
            onSurfaceVariant = on,
            surfaceTint = accent,

            inverseSurface = other,
            inverseOnSurface = otherOn,

            error = accent,
            onError = otherOn,
            errorContainer = other,
            onErrorContainer = otherOn,

            outline = on,
            outlineVariant = accent,
            scrim = Color.Black,

            surfaceBright = surfaceBg,
            surfaceDim = surfaceBg,
            surfaceContainerLowest = other,
            surfaceContainerLow = surfaceBg,
            surfaceContainer = surfaceBg,
            surfaceContainerHigh = surfaceBg,
            surfaceContainerHighest = surfaceBg,

            primaryFixed = accent,
            primaryFixedDim = accent,
            onPrimaryFixed = on,
            onPrimaryFixedVariant = on,

            secondaryFixed = accent,
            secondaryFixedDim = accent,
            onSecondaryFixed = on,
            onSecondaryFixedVariant = on,

            tertiaryFixed = other,
            tertiaryFixedDim = other,
            onTertiaryFixed = otherOn,
            onTertiaryFixedVariant = otherOn,
        )
    }
}
