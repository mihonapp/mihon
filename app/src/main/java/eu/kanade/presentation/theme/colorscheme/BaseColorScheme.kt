package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal abstract class BaseColorScheme {

    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme

    fun getColorScheme(isDark: Boolean, isAmoled: Boolean): ColorScheme {
        if (!isDark) return lightScheme

        if (!isAmoled) return darkScheme

        return darkScheme.copy(
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,

            // Cannot be pure black as there's content scrolling behind it
            // https://m3.material.io/components/navigation-bar/guidelines#90615a71-607e-485e-9e09-778bfc080563
            surfaceVariant = Color(0xFF0A0A0A),   // Navigation bar background (ThemePrefWidget)
            surfaceContainer = Color(0xFF0A0A0A), // Navigation bar background
        )
    }
}
