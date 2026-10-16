package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Afraga default palette: ink navy, electric blue, violet, cyan, and coral.
 */
internal object TachiyomiColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF07152D),
        primaryContainer = Color(0xFF1D4ED8),
        onPrimaryContainer = Color(0xFFDBEAFE),
        inversePrimary = Color(0xFF2563EB),
        secondary = Color(0xFFC4B5FD), // Violet accents and unread badge
        onSecondary = Color(0xFF24134D),
        secondaryContainer = Color(0xFF5B3BBF), // Navigation selection
        onSecondaryContainer = Color(0xFFF1EBFF),
        tertiary = Color(0xFF22D3EE), // Cyan highlight
        onTertiary = Color(0xFF083344),
        tertiaryContainer = Color(0xFF155E75),
        onTertiaryContainer = Color(0xFFCFFAFE),
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFF8FAFC),
        surface = Color(0xFF0F172A),
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFFCBD5E1),
        surfaceTint = Color(0xFF60A5FA),
        inverseSurface = Color(0xFFE2E8F0),
        inverseOnSurface = Color(0xFF0F172A),
        error = Color(0xFFFB7185), // Warm coral alert
        onError = Color(0xFF450A18),
        errorContainer = Color(0xFF881337),
        onErrorContainer = Color(0xFFFFE4E6),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF334155),
        surfaceContainerLowest = Color(0xFF0B1120),
        surfaceContainerLow = Color(0xFF131D30),
        surfaceContainer = Color(0xFF1E293B),
        surfaceContainerHigh = Color(0xFF26354C),
        surfaceContainerHighest = Color(0xFF334155),
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
