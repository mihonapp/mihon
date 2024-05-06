package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Tako theme
 * Original color scheme by ghostbear
 * M3 color scheme generated by Material Theme Builder (https://goo.gle/material-theme-builder-web)
 *
 * Key colors:
 * Primary #F3B375
 * Secondary #F3B375
 * Tertiary #66577E
 * Neutral #21212E
 */
internal object TakoColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFF3B375),
        onPrimary = Color(0xFF38294E),
        primaryContainer = Color(0xFFF3B375),
        onPrimaryContainer = Color(0xFF38294E),
        inversePrimary = Color(0xFF84531E),
        secondary = Color(0xFFF3B375), // Unread badge
        onSecondary = Color(0xFF38294E), // Unread badge text
        secondaryContainer = Color(0xFF5C4D4B), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFF3B375), // Navigation bar selector icon
        tertiary = Color(0xFF66577E), // Downloaded badge
        onTertiary = Color(0xFFF3B375), // Downloaded badge text
        tertiaryContainer = Color(0xFF4E4065),
        onTertiaryContainer = Color(0xFFEDDCFF),
        background = Color(0xFF21212E),
        onBackground = Color(0xFFE3E0F2),
        surface = Color(0xFF21212E),
        onSurface = Color(0xFFE3E0F2),
        surfaceVariant = Color(0xFF2A2A3C), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFCBC4CE),
        surfaceTint = Color(0xFF66577E),
        inverseSurface = Color(0xFFE5E1E6),
        inverseOnSurface = Color(0xFF1B1B1E),
        outline = Color(0xFF958F99),
        surfaceContainer = Color(0xFF2A2A3C), // Navigation bar background
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF66577E),
        onPrimary = Color(0xFFF3B375),
        primaryContainer = Color(0xFF66577E),
        onPrimaryContainer = Color(0xFFF3B375),
        inversePrimary = Color(0xFFD6BAFF),
        secondary = Color(0xFF66577E), // Unread badge
        onSecondary = Color(0xFFF3B375), // Unread badge text
        secondaryContainer = Color(0xFFC8BED0), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF66577E), // Navigation bar selector icon
        tertiary = Color(0xFFF3B375), // Downloaded badge
        onTertiary = Color(0xFF574360), // Downloaded badge text
        tertiaryContainer = Color(0xFFFDD6B0),
        onTertiaryContainer = Color(0xFF221437),
        background = Color(0xFFF7F5FF),
        onBackground = Color(0xFF1B1B22),
        surface = Color(0xFFF7F5FF),
        onSurface = Color(0xFF1B1B22),
        surfaceVariant = Color(0xFFE8E0EB), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF49454E),
        surfaceTint = Color(0xFF66577E),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF3EFF4),
        outline = Color(0xFF7A757E),
        surfaceContainer = Color(0xFFE8E0EB), // Navigation bar background
    )
}
