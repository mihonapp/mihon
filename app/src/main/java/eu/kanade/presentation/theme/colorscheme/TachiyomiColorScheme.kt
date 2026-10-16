package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Afraga default palette: ink navy, electric blue, violet, cyan, and coral.
 */
internal object TachiyomiColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF00B0FF),
        onPrimary = Color(0xFF001C38),
        primaryContainer = Color(0xFF007BFF),
        onPrimaryContainer = Color(0xFFE4F3FF),
        inversePrimary = Color(0xFF007BFF),
        secondary = Color(0xFFB388FF), // Violet accents and unread badge
        onSecondary = Color(0xFF24134D),
        secondaryContainer = Color(0xFF6A35A8), // Navigation selection
        onSecondaryContainer = Color(0xFFF1EBFF),
        tertiary = Color(0xFF00E5FF), // Cyan highlight
        onTertiary = Color(0xFF083344),
        tertiaryContainer = Color(0xFF155E75),
        onTertiaryContainer = Color(0xFFCFFAFE),
        background = Color(0xFF0A0E17),
        onBackground = Color(0xFFF8FAFC),
        surface = Color(0xFF0A0E17),
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF182035),
        onSurfaceVariant = Color(0xFFCBD5E1),
        surfaceTint = Color(0xFF00B0FF),
        inverseSurface = Color(0xFFE2E8F0),
        inverseOnSurface = Color(0xFF0F172A),
        error = Color(0xFFFF5252), // Warm coral alert
        onError = Color(0xFF450A18),
        errorContainer = Color(0xFF881337),
        onErrorContainer = Color(0xFFFFE4E6),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF334155),
        surfaceContainerLowest = Color(0xFF0A0E17),
        surfaceContainerLow = Color(0xFF111726),
        surfaceContainer = Color(0xFF182035),
        surfaceContainerHigh = Color(0xFF202B42),
        surfaceContainerHighest = Color(0xFF293550),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF006874),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA8F4FF),
        onPrimaryContainer = Color(0xFF001F24),
        inversePrimary = Color(0xFF70E8F2),
        secondary = Color(0xFF6545A6), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFEBDDFF), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF22005A), // Navigation bar selector icon
        tertiary = Color(0xFF006E1B), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFF95F990),
        onTertiaryContainer = Color(0xFF002203),
        background = Color(0xFFF5F9FF),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFF5F9FF),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFE9F0FA), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF414D63),
        surfaceTint = Color(0xFF006874),
        inverseSurface = Color(0xFF303034),
        inverseOnSurface = Color(0xFFF2F0F4),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF707C92),
        outlineVariant = Color(0xFFC2CDDF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEEF4FC),
        surfaceContainer = Color(0xFFE9F0FA), // Navigation bar background
        surfaceContainerHigh = Color(0xFFE1EAF6),
        surfaceContainerHighest = Color(0xFFD9E4F2),
    )
}
