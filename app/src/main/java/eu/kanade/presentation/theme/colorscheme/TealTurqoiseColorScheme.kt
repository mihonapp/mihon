package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Teal Turqoise theme
 */
internal object TealTurqoiseColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF40E0D0),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF40E0D0),
        onPrimaryContainer = Color(0xFF000000),
        inversePrimary = Color(0xFF008080),
        secondary = Color(0xFF40E0D0), // Unread badge
        onSecondary = Color(0xFF000000), // Unread badge text
        secondaryContainer = Color(0xFF18544E), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF40E0D0), // Navigation bar selector icon
        tertiary = Color(0xFFBF1F2F), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFF200508),
        onTertiaryContainer = Color(0xFFBF1F2F),
        background = Color(0xFF202125),
        onBackground = Color(0xFFDFDEDA),
        surface = Color(0xFF202125),
        onSurface = Color(0xFFDFDEDA),
        surfaceVariant = Color(0xFF233133), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFDFDEDA),
        surfaceTint = Color(0xFF40E0D0),
        inverseSurface = Color(0xFFDFDEDA),
        inverseOnSurface = Color(0xFF202125),
        outline = Color(0xFF899391),
        surfaceContainer = Color(0xFF233133), // Navigation bar background
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF008080),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF008080),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF40E0D0),
        secondary = Color(0xFF008080), // Unread badge text
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFCFE5E4), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF008080), // Navigation bar selector icon
        tertiary = Color(0xFFFF7F7F), // Downloaded badge
        onTertiary = Color(0xFF000000), // Downloaded badge text
        tertiaryContainer = Color(0xFF2A1616),
        onTertiaryContainer = Color(0xFFFF7F7F),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF050505),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF050505),
        surfaceVariant = Color(0xFFEBF3F1), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF050505),
        surfaceTint = Color(0xFFBFDFDF),
        inverseSurface = Color(0xFF050505),
        inverseOnSurface = Color(0xFFFAFAFA),
        outline = Color(0xFF6F7977),
        surfaceContainer = Color(0xFFEBF3F1), // Navigation bar background
    )
}
