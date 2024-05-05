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
        )
    }
}
