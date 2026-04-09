package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Tokyo Night theme
 * Based on the Tokyo Night color scheme by folke
 * https://github.com/folke/tokyonight.nvim
 *
 * Key colors:
 * Primary (blue) #7AA2F7
 * Secondary (purple) #BB9AF7
 * Tertiary (cyan) #7DCFFF
 * Background #1A1B26
 */
internal object TokyoNightColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF1A1B26),
        primaryContainer = Color(0xFF3D59A1),
        onPrimaryContainer = Color(0xFFC0CAF5),
        inversePrimary = Color(0xFF2E7DE9),
        secondary = Color(0xFFBB9AF7), // Unread badge
        onSecondary = Color(0xFF1A1B26), // Unread badge text
        secondaryContainer = Color(0xFF504072), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFBB9AF7), // Navigation bar selector icon
        tertiary = Color(0xFF7DCFFF), // Downloaded badge
        onTertiary = Color(0xFF1A1B26), // Downloaded badge text
        tertiaryContainer = Color(0xFF2A6485),
        onTertiaryContainer = Color(0xFFC0CAF5),
        background = Color(0xFF1A1B26),
        onBackground = Color(0xFFC0CAF5),
        surface = Color(0xFF1A1B26),
        onSurface = Color(0xFFC0CAF5),
        surfaceVariant = Color(0xFF292E42), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFA9B1D6),
        surfaceTint = Color(0xFF7AA2F7),
        inverseSurface = Color(0xFFC0CAF5),
        inverseOnSurface = Color(0xFF1A1B26),
        outline = Color(0xFF565F89),
        outlineVariant = Color(0xFF3B4261),
        error = Color(0xFFF7768E),
        onError = Color(0xFF1A1B26),
        errorContainer = Color(0xFFF7768E),
        onErrorContainer = Color(0xFF1A1B26),
        surfaceContainerLowest = Color(0xFF16161E),
        surfaceContainerLow = Color(0xFF1E1F2B),
        surfaceContainer = Color(0xFF292E42), // Navigation bar background
        surfaceContainerHigh = Color(0xFF2F3449),
        surfaceContainerHighest = Color(0xFF3B4261),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF2E7DE9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB7C1E3),
        onPrimaryContainer = Color(0xFF1A1B26),
        inversePrimary = Color(0xFF7AA2F7),
        secondary = Color(0xFF9854F1), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFCBB8F0), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF7847BD), // Navigation bar selector icon
        tertiary = Color(0xFF007197), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFA8D8E8),
        onTertiaryContainer = Color(0xFF1A1B26),
        background = Color(0xFFE1E2E7),
        onBackground = Color(0xFF3760BF),
        surface = Color(0xFFE1E2E7),
        onSurface = Color(0xFF3760BF),
        surfaceVariant = Color(0xFFD0D5E3), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF6172B0),
        surfaceTint = Color(0xFF2E7DE9),
        inverseSurface = Color(0xFF3760BF),
        inverseOnSurface = Color(0xFFE1E2E7),
        outline = Color(0xFF848CB5),
        outlineVariant = Color(0xFFA8AECB),
        error = Color(0xFFF52A65),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF52A65),
        onErrorContainer = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFC4C8DA),
        surfaceContainerLow = Color(0xFFCDD2E1),
        surfaceContainer = Color(0xFFD0D5E3), // Navigation bar background
        surfaceContainerHigh = Color(0xFFDCDFE9),
        surfaceContainerHighest = Color(0xFFE8EAF0),
    )
}
