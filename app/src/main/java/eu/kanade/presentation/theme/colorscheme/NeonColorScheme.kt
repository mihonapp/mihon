package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Neon: pure black surfaces with an electric blue accent, outlined and glowing.
 */
internal object NeonColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF3D8BFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF0A2F66),
        onPrimaryContainer = Color(0xFFCFE1FF),
        inversePrimary = Color(0xFF0B5BD3),
        secondary = Color(0xFF66B2FF),
        onSecondary = Color(0xFF001C3A),
        secondaryContainer = Color(0xFF0E2A4D),
        onSecondaryContainer = Color(0xFFCFE5FF),
        tertiary = Color(0xFF8C7BFF),
        onTertiary = Color(0xFF140A4D),
        tertiaryContainer = Color(0xFF2A1F73),
        onTertiaryContainer = Color(0xFFE2DCFF),
        error = Color(0xFFFF6B7A),
        onError = Color(0xFF2D0007),
        errorContainer = Color(0xFF7A1424),
        onErrorContainer = Color(0xFFFFD9DC),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF2F6FF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFF2F6FF),
        surfaceVariant = Color(0xFF101827),
        onSurfaceVariant = Color(0xFFA7B4CC),
        surfaceTint = Color(0xFF3D8BFF),
        inverseSurface = Color(0xFFE6EEFF),
        inverseOnSurface = Color(0xFF0A0F1A),
        outline = Color(0xFF2B5CA8),
        outlineVariant = Color(0xFF16294A),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF1C2940),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF060A12),
        surfaceContainer = Color(0xFF0A101C),
        surfaceContainerHigh = Color(0xFF101828),
        surfaceContainerHighest = Color(0xFF172235),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF0B6BFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E4FF),
        onPrimaryContainer = Color(0xFF001A43),
        inversePrimary = Color(0xFFA9C7FF),
        secondary = Color(0xFF1E7BE0),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3E6FF),
        onSecondaryContainer = Color(0xFF001C38),
        tertiary = Color(0xFF5B4BD6),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE4DFFF),
        onTertiaryContainer = Color(0xFF170B5C),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF7F9FF),
        onBackground = Color(0xFF0B1220),
        surface = Color(0xFFF7F9FF),
        onSurface = Color(0xFF0B1220),
        surfaceVariant = Color(0xFFE1E8F5),
        onSurfaceVariant = Color(0xFF424C5E),
        surfaceTint = Color(0xFF0B6BFF),
        inverseSurface = Color(0xFF1C2433),
        inverseOnSurface = Color(0xFFEEF2FB),
        outline = Color(0xFF7FA6E6),
        outlineVariant = Color(0xFFC5D5F0),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFFD7DEEA),
        surfaceBright = Color(0xFFF7F9FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F4FC),
        surfaceContainer = Color(0xFFEAF0FA),
        surfaceContainerHigh = Color(0xFFE4EBF7),
        surfaceContainerHighest = Color(0xFFDEE6F4),
    )
}
