package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Lavender theme
 * Color scheme by Osyx
 *
 * Key colors:
 * Primary #A177FF
 * Secondary #A177FF
 * Tertiary #5E25E1
 * Neutral #111129
 */
internal object LavenderColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA177FF),
        onPrimary = Color(0xFF111129),
        primaryContainer = Color(0xFFA177FF),
        onPrimaryContainer = Color(0xFF111129),
        inversePrimary = Color(0xFF006D2F),
        secondary = Color(0xFFA177FF),
        onSecondary = Color(0xFF111129),
        secondaryContainer = Color(0xFFA177FF),
        onSecondaryContainer = Color(0xFF111129),
        tertiary = Color(0xFF5E25E1),
        onTertiary = Color(0xFFE8E8E8),
        tertiaryContainer = Color(0xFF111129),
        onTertiaryContainer = Color(0xFFDEE8FF),
        background = Color(0xFF111129),
        onBackground = Color(0xFFDEE8FF),
        surface = Color(0xFF111129),
        onSurface = Color(0xFFDEE8FF),
        surfaceVariant = Color(0x2CB6B6B6),
        onSurfaceVariant = Color(0xFFE8E8E8),
        surfaceTint = Color(0xFFA177FF),
        inverseSurface = Color(0xFF221247),
        inverseOnSurface = Color(0xFFDEE8FF),
        outline = Color(0xA8905FFF),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF7B46AF),
        onPrimary = Color(0xFFEDE2FF),
        primaryContainer = Color(0xFF7B46AF),
        onPrimaryContainer = Color(0xFFEDE2FF),
        inversePrimary = Color(0xFFD6BAFF),
        secondary = Color(0xFF7B46AF),
        onSecondary = Color(0xFFEDE2FF),
        secondaryContainer = Color(0xFF7B46AF),
        onSecondaryContainer = Color(0xFFEDE2FF),
        tertiary = Color(0xFFEDE2FF),
        onTertiary = Color(0xFF7B46AF),
        tertiaryContainer = Color(0xFFEDE2FF),
        onTertiaryContainer = Color(0xFF7B46AF),
        background = Color(0xFFEDE2FF),
        onBackground = Color(0xFF1B1B22),
        surface = Color(0xFFEDE2FF),
        onSurface = Color(0xFF1B1B22),
        surfaceVariant = Color(0xFFB9B0CC),
        onSurfaceVariant = Color(0xD849454E),
        surfaceTint = Color(0xFF7B46AF),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF3EFF4),
        outline = Color(0xFF7B46AF),
    )
}
