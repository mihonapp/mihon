package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Nord theme
 * https://www.nordtheme.com/docs/colors-and-palettes
 * for the light theme, the primary color is switched with the tertiary for better contrast in some case
 */
internal object NordColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF2E3440),
        primaryContainer = Color(0xFF88C0D0),
        onPrimaryContainer = Color(0xFF2E3440),
        inversePrimary = Color(0xFF397E91),
        secondary = Color(0xFF81A1C1),
        onSecondary = Color(0xFF2E3440),
        secondaryContainer = Color(0xFF81A1C1),
        onSecondaryContainer = Color(0xFF2E3440),
        tertiary = Color(0xFF5E81AC),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF5E81AC),
        onTertiaryContainer = Color(0xFF000000),
        background = Color(0xFF2E3440),
        onBackground = Color(0xFFECEFF4),
        surface = Color(0xFF3B4252),
        onSurface = Color(0xFFECEFF4),
        surfaceVariant = Color(0xFF2E3440),
        onSurfaceVariant = Color(0xFFECEFF4),
        surfaceTint = Color(0xFF88C0D0),
        inverseSurface = Color(0xFFD8DEE9),
        inverseOnSurface = Color(0xFF2E3440),
        outline = Color(0xFF6d717b),
        outlineVariant = Color(0xFF90939a),
        onError = Color(0xFF2E3440),
        errorContainer = Color(0xFFBF616A),
        onErrorContainer = Color(0xFF000000),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF5E81AC),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF5E81AC),
        onPrimaryContainer = Color(0xFF000000),
        inversePrimary = Color(0xFF8CA8CD),
        secondary = Color(0xFF81A1C1),
        onSecondary = Color(0xFF2E3440),
        secondaryContainer = Color(0xFF81A1C1),
        onSecondaryContainer = Color(0xFF2E3440),
        tertiary = Color(0xFF88C0D0),
        onTertiary = Color(0xFF2E3440),
        tertiaryContainer = Color(0xFF88C0D0),
        onTertiaryContainer = Color(0xFF2E3440),
        background = Color(0xFFECEFF4),
        onBackground = Color(0xFF2E3440),
        surface = Color(0xFFE5E9F0),
        onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFffffff),
        onSurfaceVariant = Color(0xFF2E3440),
        surfaceTint = Color(0xFF5E81AC),
        inverseSurface = Color(0xFF3B4252),
        inverseOnSurface = Color(0xFFECEFF4),
        outline = Color(0xFF2E3440),
        onError = Color(0xFFECEFF4),
        errorContainer = Color(0xFFBF616A),
        onErrorContainer = Color(0xFF000000),
    )
}
