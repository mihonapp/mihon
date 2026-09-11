package mihon.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import mihon.desktop.preferences.ThemeMode
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.ui.theme.ThemeRegistry

@Composable
fun MihonDesktopTheme(
    themeMode: ThemeMode = ThemeMode.System,
    appTheme: DesktopAppTheme = DesktopAppTheme.DEFAULT,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val baseScheme = ThemeRegistry.getColorScheme(appTheme)
    val colorScheme = baseScheme.getColorScheme(isDark = dark, isAmoled = isAmoled)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
