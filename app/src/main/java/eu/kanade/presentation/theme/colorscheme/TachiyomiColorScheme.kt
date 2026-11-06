package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Default theme.
 *
 * Mekuri: the Default theme carries Mekuri's colours (deep ink, lavender, coral).
 * Key colors: Primary #5B3FD9 / #B9A8FF, Secondary (unread) coral #FF8A6B, Tertiary (downloaded) mint #7FD8BC
 */
internal object TachiyomiColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFB9A8FF),
        onPrimary = Color(0xFF24186B),
        primaryContainer = Color(0xFF3A2A8C),
        onPrimaryContainer = Color(0xFFE6DEFF),
        inversePrimary = Color(0xFF5B3FD9),
        secondary = Color(0xFFFF8A6B), // Unread badge
        onSecondary = Color(0xFF3A1206), // Unread badge text
        secondaryContainer = Color(0xFF2E2648), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFD6CCFF), // Navigation bar selector icon
        tertiary = Color(0xFF7FD8BC), // Downloaded badge
        onTertiary = Color(0xFF00382B), // Downloaded badge text
        tertiaryContainer = Color(0xFF0F5142),
        onTertiaryContainer = Color(0xFF9FF5D8),
        background = Color(0xFF0F0E15),
        onBackground = Color(0xFFE8E5F2),
        surface = Color(0xFF0F0E15),
        onSurface = Color(0xFFE8E5F2),
        surfaceVariant = Color(0xFF1C1A26), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFC9C4D9),
        surfaceTint = Color(0xFFB9A8FF),
        inverseSurface = Color(0xFFE8E5F2),
        inverseOnSurface = Color(0xFF1C1A26),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF928CA6),
        outlineVariant = Color(0xFF47435A),
        surfaceContainerLowest = Color(0xFF0B0A10),
        surfaceContainerLow = Color(0xFF15131D),
        surfaceContainer = Color(0xFF1C1A26), // Navigation bar background
        surfaceContainerHigh = Color(0xFF24212F),
        surfaceContainerHighest = Color(0xFF2C2938),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF5B3FD9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE6DEFF),
        onPrimaryContainer = Color(0xFF1B0F5C),
        inversePrimary = Color(0xFFB9A8FF),
        secondary = Color(0xFFD9532F), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFE9E3FB), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF2A1F66), // Navigation bar selector icon
        tertiary = Color(0xFF1B7A62), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFA8F2D9),
        onTertiaryContainer = Color(0xFF002118),
        background = Color(0xFFFBFAFF),
        onBackground = Color(0xFF1B1A22),
        surface = Color(0xFFFBFAFF),
        onSurface = Color(0xFF1B1A22),
        surfaceVariant = Color(0xFFF1EEF8), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF4A4659),
        surfaceTint = Color(0xFF5B3FD9),
        inverseSurface = Color(0xFF302E38),
        inverseOnSurface = Color(0xFFF3F0FA),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF787389),
        outlineVariant = Color(0xFFCBC6DA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F3FC),
        surfaceContainer = Color(0xFFF1EEF8), // Navigation bar background
        surfaceContainerHigh = Color(0xFFEBE7F4),
        surfaceContainerHighest = Color(0xFFE5E1EF),
    )
}
