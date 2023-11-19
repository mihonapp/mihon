package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal abstract class BaseColorScheme {

    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme

    fun getColorScheme(isDark: Boolean, isAmoled: Boolean): ColorScheme {
        return (if (isDark) darkScheme else lightScheme)
            .let {
                if (isDark && isAmoled) {
                    it.copy(
                        background = Color.Black,
                        onBackground = Color.White,
                        surface = Color.Black,
                        onSurface = Color.White,
                    )
                } else {
                    it
                }
            }
    }
}
