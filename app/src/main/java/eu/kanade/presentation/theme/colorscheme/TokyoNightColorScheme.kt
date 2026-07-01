package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Tokyo Night Moon/Day
 *
 * https://github.com/folke/tokyonight.nvim
 */
internal object TokyoNightColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF82AAFF),
        onPrimary = Color(0xFF1B1D2B),
        primaryContainer = Color(0xFF444A73),
        onPrimaryContainer = Color(0xFF9AB8FF),
        secondary = Color(0xFF86E1FC),
        onSecondary = Color(0xFF1B1D2B),
        secondaryContainer = Color(0xFF444A73),
        onSecondaryContainer = Color(0xFFB2EBFF),
        tertiary = Color(0xFFC099FF),
        onTertiary = Color(0xFF1B1D2B),
        tertiaryContainer = Color(0xFF444A73),
        onTertiaryContainer = Color(0xFFCAABFF),
        error = Color(0xFFFF757F),
        onError = Color(0xFF1B1D2B),
        errorContainer = Color(0xFFC53B53),
        onErrorContainer = Color(0xFFFF8D94),
        background = Color(0xFF222436),
        onBackground = Color(0xFFC8D3F5),
        surface = Color(0xFF222436),
        onSurface = Color(0xFFC8D3F5),
        surfaceVariant = Color(0xFF444A73),
        onSurfaceVariant = Color(0xFF828BB8),
        outline = Color(0xFF828BB8),
        outlineVariant = Color(0xFF444A73),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFC8D3F5),
        inverseOnSurface = Color(0xFF222436),
        inversePrimary = Color(0xFF3D5C9E),
        surfaceContainerLowest = Color(0xFF1E202F),
        surfaceDim = Color(0xFF222436),
        surfaceContainerLow = Color(0xFF292C44),
        surfaceContainer = Color(0xFF2D304B),
        surfaceContainerHigh = Color(0xFF363B5D),
        surfaceContainerHighest = Color(0xFF3F466F),
        surfaceBright = Color(0xFF434A77),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF2E7DE9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF92A6D5),
        onPrimaryContainer = Color(0xFF15386A),
        inversePrimary = Color(0xFF7890DD),
        secondary = Color(0xFF007197),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFA9C6D3),
        onSecondaryContainer = Color(0xFF006A83),
        tertiary = Color(0xFF9854F1),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCFBFEA),
        onTertiaryContainer = Color(0xFF7847BD),
        background = Color(0xFFE1E2E7),
        onBackground = Color(0xFF3760BF),
        surface = Color(0xFFE1E2E7),
        onSurface = Color(0xFF3760BF),
        surfaceVariant = Color(0xFFC4C8DA),
        onSurfaceVariant = Color(0xFF6172B0),
        surfaceTint = Color(0xFF2E7DE9),
        inverseSurface = Color(0xFF3760BF),
        inverseOnSurface = Color(0xFFE1E2E7),
        outline = Color(0xFF68709A),
        outlineVariant = Color(0xFFA8AECB),
        error = Color(0xFFF52A65),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFE6B4C7),
        onErrorContainer = Color(0xFF6E132D),
        surfaceDim = Color(0xFF9195B1),
        surfaceBright = Color(0xFFE1E2E7),
        surfaceContainerLowest = Color(0xFFE4E5E9),
        surfaceContainerLow = Color(0xFFD0D5E3),
        surfaceContainer = Color(0xFFC1C9DF),
        surfaceContainerHigh = Color(0xFFC4C8DA),
        surfaceContainerHighest = Color(0xFFA1A6C5),
    )
}
