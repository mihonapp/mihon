package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Tidal Wave theme
 * Original color scheme by NahutabDevelop
 *
 * Key colors:
 * Primary #004152
 * Secondary #5ed4fc
 * Tertiary #92f7bc
 * Neutral #16151D
 */
internal object TidalWaveColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF5ed4fc),
        onPrimary = Color(0xFF003544),
        primaryContainer = Color(0xFF004d61),
        onPrimaryContainer = Color(0xFFb8eaff),
        inversePrimary = Color(0xFFa12b03),
        secondary = Color(0xFF5ed4fc), // Unread badge
        onSecondary = Color(0xFF003544), // Unread badge text
        secondaryContainer = Color(0xFF004d61), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFb8eaff), // Navigation bar selector icon
        tertiary = Color(0xFF92f7bc), // Downloaded badge
        onTertiary = Color(0xFF001c3b), // Downloaded badge text
        tertiaryContainer = Color(0xFFc3fada),
        onTertiaryContainer = Color(0xFF78ffd6),
        background = Color(0xFF001c3b),
        onBackground = Color(0xFFd5e3ff),
        surface = Color(0xFF001c3b),
        onSurface = Color(0xFFd5e3ff),
        surfaceVariant = Color(0xFF082b4b), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFbfc8cc),
        surfaceTint = Color(0xFF5ed4fc),
        inverseSurface = Color(0xFFffe3c4),
        inverseOnSurface = Color(0xFF001c3b),
        outline = Color(0xFF8a9296),
        surfaceContainerLowest = Color(0xFF072642),
        surfaceContainerLow = Color(0xFF072947),
        surfaceContainer = Color(0xFF082b4b), // Navigation bar background
        surfaceContainerHigh = Color(0xFF093257),
        surfaceContainerHighest = Color(0xFF0A3861),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF006780),
        onPrimary = Color(0xFFffffff),
        primaryContainer = Color(0xFFB4D4DF),
        onPrimaryContainer = Color(0xFF001f28),
        inversePrimary = Color(0xFFff987f),
        secondary = Color(0xFF006780), // Unread badge
        onSecondary = Color(0xFFffffff), // Unread badge text
        secondaryContainer = Color(0xFF9AE1FF), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF001f28), // Navigation bar selector icon
        tertiary = Color(0xFF92f7bc), // Downloaded badge
        onTertiary = Color(0xFF001c3b), // Downloaded badge text
        tertiaryContainer = Color(0xFFc3fada),
        onTertiaryContainer = Color(0xFF78ffd6),
        background = Color(0xFFfdfbff),
        onBackground = Color(0xFF001c3b),
        surface = Color(0xFFfdfbff),
        onSurface = Color(0xFF001c3b),
        surfaceVariant = Color(0xFFe8eff5), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF40484c),
        surfaceTint = Color(0xFF006780),
        inverseSurface = Color(0xFF020400),
        inverseOnSurface = Color(0xFFffe3c4),
        outline = Color(0xFF70787c),
        surfaceContainerLowest = Color(0xFFe2e8ec),
        surfaceContainerLow = Color(0xFFe5ecf1),
        surfaceContainer = Color(0xFFe8eff5), // Navigation bar background
        surfaceContainerHigh = Color(0xFFedf4fA),
        surfaceContainerHighest = Color(0xFFf5faff),
    )
}
