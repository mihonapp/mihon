package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Tokyo Night theme
 * https://github.com/folke/tokyonight.nvim
 */
internal object TokyoNightColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF7AA2F7), // Blue - main accent
        onPrimary = Color(0xFF1A1B2E),
        primaryContainer = Color(0xFF3D59A1),
        onPrimaryContainer = Color(0xFFCCD9FF),
        inversePrimary = Color(0xFF3D59A1),
        secondary = Color(0xFF9ECE6A), // Green - unread badge
        onSecondary = Color(0xFF1A1B2E), // Unread badge text
        secondaryContainer = Color(0xFF3D7A47), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF9ECE6A), // Navigation bar selector icon
        tertiary = Color(0xFFBB9AF7), // Purple - downloaded badge
        onTertiary = Color(0xFF1A1B2E), // Downloaded badge text
        tertiaryContainer = Color(0xFF6A3FAF),
        onTertiaryContainer = Color(0xFFE2D4FF),
        background = Color(0xFF1A1B2E),
        onBackground = Color(0xFFC0CAF5),
        surface = Color(0xFF1A1B2E),
        onSurface = Color(0xFFC0CAF5),
        surfaceVariant = Color(0xFF24283B), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFA9B1D6),
        surfaceTint = Color(0xFF7AA2F7),
        inverseSurface = Color(0xFFC0CAF5),
        inverseOnSurface = Color(0xFF1A1B2E),
        outline = Color(0xFF565F89),
        outlineVariant = Color(0xFF414868),
        onError = Color(0xFF1A1B2E),
        errorContainer = Color(0xFFF7768E),
        onErrorContainer = Color(0xFF1A1B2E),
        surfaceContainerLowest = Color(0xFF13141F),
        surfaceContainerLow = Color(0xFF1E2030),
        surfaceContainer = Color(0xFF24283B),
        surfaceContainerHigh = Color(0xFF2A2F45),
        surfaceContainerHighest = Color(0xFF2F3549), // Navigation bar background
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF3760BF), // Darker blue for light mode
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD0DFFF),
        onPrimaryContainer = Color(0xFF0A2472),
        inversePrimary = Color(0xFF7AA2F7),
        secondary = Color(0xFF485E30), // Green
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC8ECAA), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF485E30), // Navigation bar selector icon
        tertiary = Color(0xFF5A3E8A), // Purple
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEADDFF),
        onTertiaryContainer = Color(0xFF3B1F6E),
        background = Color(0xFFE1E2F0),
        onBackground = Color(0xFF343B58),
        surface = Color(0xFFD5D6E8),
        onSurface = Color(0xFF343B58),
        surfaceVariant = Color(0xFFCACADF), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF343B58),
        surfaceTint = Color(0xFF3760BF),
        inverseSurface = Color(0xFF343B58),
        inverseOnSurface = Color(0xFFE1E2F0),
        outline = Color(0xFF343B58),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFB3BA),
        onErrorContainer = Color(0xFF5C0009),
        surfaceContainerLowest = Color(0xFFBBBCCF),
        surfaceContainerLow = Color(0xFFC2C3D8),
        surfaceContainer = Color(0xFFCACADF), // Navigation bar background
        surfaceContainerHigh = Color(0xFFD8D9EC),
        surfaceContainerHighest = Color(0xFFE9EAF5),
    )
}
