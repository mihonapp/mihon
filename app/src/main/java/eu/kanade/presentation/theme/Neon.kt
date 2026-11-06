package eu.kanade.presentation.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Rounder corners across the app, for the card-heavy Neon look.
 */
internal val NeonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Material type scale with bolder titles and headlines, so screen and section headers stand out.
 */
internal val NeonTypography: Typography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

object NeonDefaults {
    /** A card holding a cover and its title. */
    val CardShape: Shape = RoundedCornerShape(16.dp)

    /** A cover inside a card. */
    val CoverShape: Shape = RoundedCornerShape(12.dp)

    /** A large featured card. */
    val HeroShape: Shape = RoundedCornerShape(24.dp)

    /** A fully rounded control, like a search field or a segmented tab row. */
    val PillShape: Shape = RoundedCornerShape(percent = 50)

    /**
     * The accent gradient for selected controls: a pale blue that deepens into the primary color.
     */
    val accentGradient: Brush
        @Composable
        @ReadOnlyComposable
        get() {
            val primary = MaterialTheme.colorScheme.primary
            return Brush.horizontalGradient(listOf(lerp(primary, Color.White, 0.55f), primary))
        }

    /** The thin outline cards and controls get. */
    val outlineColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
}

/**
 * A soft glow of [color] around [shape]. Colored shadows show from Android 9, a neutral one before.
 */
fun Modifier.neonGlow(shape: Shape, color: Color, elevation: Dp = 10.dp): Modifier =
    shadow(elevation = elevation, shape = shape, clip = false, ambientColor = color, spotColor = color)

/**
 * A thin [color] outline along [shape].
 */
fun Modifier.neonOutline(shape: Shape, color: Color, width: Dp = 1.dp): Modifier = border(width, color, shape)
