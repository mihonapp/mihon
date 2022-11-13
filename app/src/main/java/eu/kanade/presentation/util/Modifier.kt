package eu.kanade.presentation.util

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumTouchTargetEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import kotlin.math.roundToInt

fun Modifier.selectedBackground(isSelected: Boolean): Modifier = composed {
    if (isSelected) {
        val alpha = if (isSystemInDarkTheme()) 0.16f else 0.22f
        background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha))
    } else {
        this
    }
}

fun Modifier.secondaryItemAlpha(): Modifier = this.alpha(SecondaryItemAlpha)

fun Modifier.clickableNoIndication(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/**
 * For TextField, the provided [action] will be invoked when
 * physical enter key is pressed.
 *
 * Naturally, the TextField should be set to single line only.
 */
fun Modifier.runOnEnterKeyPressed(action: () -> Unit): Modifier = this.onPreviewKeyEvent {
    when (it.key) {
        Key.Enter, Key.NumPadEnter -> {
            action()
            true
        }
        else -> false
    }
}

@Suppress("ModifierInspectorInfo")
fun Modifier.minimumTouchTargetSize(): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "minimumTouchTargetSize"
        properties["README"] = "Adds outer padding to measure at least 48.dp (default) in " +
            "size to disambiguate touch interactions if the element would measure smaller"
    },
) {
    if (LocalMinimumTouchTargetEnforcement.current) {
        val size = LocalViewConfiguration.current.minimumTouchTargetSize
        MinimumTouchTargetModifier(size)
    } else {
        Modifier
    }
}

private class MinimumTouchTargetModifier(val size: DpSize) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)

        // Be at least as big as the minimum dimension in both dimensions
        val width = maxOf(placeable.width, size.width.roundToPx())
        val height = maxOf(placeable.height, size.height.roundToPx())

        return layout(width, height) {
            val centerX = ((width - placeable.width) / 2f).roundToInt()
            val centerY = ((height - placeable.height) / 2f).roundToInt()
            placeable.place(centerX, centerY)
        }
    }

    override fun equals(other: Any?): Boolean {
        val otherModifier = other as? MinimumTouchTargetModifier ?: return false
        return size == otherModifier.size
    }

    override fun hashCode(): Int {
        return size.hashCode()
    }
}
