package eu.kanade.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NeonTone(
    val fill: Color,
    val border: Color,
    val text: Color,
)

object AfragaNeon {
    val Emerald = NeonTone(Color(0x2600E676), Color(0xFF00E676), Color(0xFF00E676))
    val Cyan = NeonTone(Color(0x2E00B0FF), Color(0xFF00B0FF), Color(0xFF40C4FF))
    val Purple = NeonTone(Color(0x26D500F9), Color(0xFFD500F9), Color(0xFFE040FB))
    val Amber = NeonTone(Color(0x26FF9100), Color(0xFFFF9100), Color(0xFFFFAB40))
}

/** A lightweight translucent panel with a soft, GPU-rendered shadow and a drawn neon rim. */
fun Modifier.glassSurface(
    shape: Shape,
    tint: Color = Color(0x8C191E30),
    border: Color = Color.White.copy(alpha = 0.14f),
    glow: Color? = null,
    elevation: Dp = 8.dp,
    cornerRadius: Dp = 18.dp,
): Modifier = this
    .clip(shape)
    .graphicsLayer {
        this.shape = shape
        clip = true
        shadowElevation = elevation.toPx()
    }
    .drawBehind {
        val radius = CornerRadius(cornerRadius.toPx())
        drawRoundRect(color = tint, cornerRadius = radius)
        glow?.let { accent ->
            drawRoundRect(
                color = accent.copy(alpha = 0.08f),
                cornerRadius = radius,
                style = Stroke(width = 7.dp.toPx()),
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.18f),
                cornerRadius = radius,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(border, border.copy(alpha = 0.38f), glow?.copy(alpha = 0.72f) ?: border),
            ),
            cornerRadius = radius,
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }

@Composable
fun NeonPill(
    text: String,
    tone: NeonTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 520f),
        label = "neonPillPress",
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (pressed) 2.dp.toPx() else 7.dp.toPx()
            }
            .clip(CircleShape)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(color = tone.fill, cornerRadius = radius)
                drawRoundRect(
                    color = tone.border.copy(alpha = 0.14f),
                    cornerRadius = radius,
                    style = Stroke(width = 5.dp.toPx()),
                )
                drawRoundRect(
                    color = tone.border,
                    cornerRadius = radius,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = tone.text)
        }
        Text(
            text = text,
            color = tone.text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun SplitColorHeader(
    firstWord: String,
    secondWord: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = firstWord.uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = secondWord.uppercase(),
            color = Color(0xFF00E5FF),
            style = MaterialTheme.typography.titleSmall,
            letterSpacing = 1.5.sp,
        )
    }
}
