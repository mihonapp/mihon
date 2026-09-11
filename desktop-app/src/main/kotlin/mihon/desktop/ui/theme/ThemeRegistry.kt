package mihon.desktop.ui.theme

import mihon.desktop.ui.theme.colorscheme.BaseColorScheme
import mihon.desktop.ui.theme.colorscheme.CatppuccinColorScheme
import mihon.desktop.ui.theme.colorscheme.GreenAppleColorScheme
import mihon.desktop.ui.theme.colorscheme.LavenderColorScheme
import mihon.desktop.ui.theme.colorscheme.MidnightDuskColorScheme
import mihon.desktop.ui.theme.colorscheme.MonochromeColorScheme
import mihon.desktop.ui.theme.colorscheme.NordColorScheme
import mihon.desktop.ui.theme.colorscheme.StrawberryColorScheme
import mihon.desktop.ui.theme.colorscheme.TachiyomiColorScheme
import mihon.desktop.ui.theme.colorscheme.TakoColorScheme
import mihon.desktop.ui.theme.colorscheme.TealTurqoiseColorScheme
import mihon.desktop.ui.theme.colorscheme.TidalWaveColorScheme
import mihon.desktop.ui.theme.colorscheme.TokyoNightColorScheme
import mihon.desktop.ui.theme.colorscheme.YinYangColorScheme
import mihon.desktop.ui.theme.colorscheme.YotsubaColorScheme

object ThemeRegistry {
    val colorSchemes: Map<DesktopAppTheme, BaseColorScheme> = mapOf(
        DesktopAppTheme.DEFAULT to TachiyomiColorScheme,
        DesktopAppTheme.CATPPUCCIN to CatppuccinColorScheme,
        DesktopAppTheme.TOKYONIGHT to TokyoNightColorScheme,
        DesktopAppTheme.GREEN_APPLE to GreenAppleColorScheme,
        DesktopAppTheme.LAVENDER to LavenderColorScheme,
        DesktopAppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
        DesktopAppTheme.MONOCHROME to MonochromeColorScheme,
        DesktopAppTheme.NORD to NordColorScheme,
        DesktopAppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
        DesktopAppTheme.TAKO to TakoColorScheme,
        DesktopAppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
        DesktopAppTheme.TIDAL_WAVE to TidalWaveColorScheme,
        DesktopAppTheme.YINYANG to YinYangColorScheme,
        DesktopAppTheme.YOTSUBA to YotsubaColorScheme,
    )

    fun getColorScheme(theme: DesktopAppTheme): BaseColorScheme {
        return colorSchemes[theme] ?: TachiyomiColorScheme
    }
}
