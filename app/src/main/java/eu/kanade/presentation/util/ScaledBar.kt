package eu.kanade.presentation.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Renders [content] at its natural size, then visually scales the whole
 * thing down by [scale] and reports the *scaled-down* size to the layout
 * tree — so the space actually reserved for [content] (e.g. in a Scaffold's
 * topBar/bottomBar slot) shrinks too, instead of just visually shrinking
 * while still reserving the original footprint.
 *
 * Unlike forcing a smaller [Modifier.height] directly on a prebuilt bar
 * (which can clip or squash its internal Material layout if the bar can't
 * actually lay itself out any shorter), this measures the bar in a
 * proportionally *larger* virtual space first, then shrinks the entire
 * rendered result uniformly around its own center — so icons, text, and
 * padding all shrink together with no clipping or distortion, like a clean
 * zoom-out. The math is set up so the shrunk content exactly fills the
 * smaller reported box (no overflow past the edges, no gap inside it),
 * regardless of whether the caller's parent then places that box at the
 * top or bottom of the screen.
 *
 * @param scale 1f = unscaled (no-op). Values below 1f shrink the bar;
 *   coerced into a sane (0.01f, 1f] range.
 */
@Composable
fun ScaledBar(
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val safeScale = scale.coerceIn(0.01f, 1f)

    if (safeScale >= 0.999f) {
        // No-op fast path: skip the custom measuring entirely when unscaled.
        Box(modifier = modifier) { content() }
        return
    }

    Layout(
        content = content,
        // Default (center) transformOrigin is intentional here — paired
        // with the centered placement offset below, it's what makes the
        // math work out to exactly fill the final reported box.
        modifier = modifier.graphicsLayer(
            scaleX = safeScale,
            scaleY = safeScale,
        ),
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
            ?: return@Layout layout(0, 0) {}

        val inverseScale = 1f / safeScale

        // Measure the child in an enlarged virtual space (its natural size,
        // just inflated), so its internal layout never has to compress.
        // When a dimension is unbounded, reuse the original (already
        // "unbounded") value as-is rather than referencing a sentinel
        // constant directly — inflating "unbounded" is a no-op anyway.
        val childConstraints = Constraints(
            minWidth = (constraints.minWidth * inverseScale).toInt(),
            maxWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth * inverseScale).toInt()
            } else {
                constraints.maxWidth
            },
            minHeight = (constraints.minHeight * inverseScale).toInt(),
            maxHeight = if (constraints.hasBoundedHeight) {
                (constraints.maxHeight * inverseScale).toInt()
            } else {
                constraints.maxHeight
            },
        )

        val placeable = measurable.measure(childConstraints)

        // Report the scaled-down (smaller) footprint to the layout tree.
        val finalWidth = (placeable.width * safeScale).toInt()
        val finalHeight = (placeable.height * safeScale).toInt()

        // Center the natural-size child over the smaller final box (this
        // offset is negative when scale < 1, i.e. the child is placed
        // straddling outside the box on all sides). Combined with the
        // default center transformOrigin on the graphicsLayer above, the
        // scale transform shrinks the content exactly onto [0, finalWidth]
        // x [0, finalHeight] — no overflow, no empty gap.
        val offsetX = (finalWidth - placeable.width) / 2
        val offsetY = (finalHeight - placeable.height) / 2

        layout(finalWidth, finalHeight) {
            placeable.placeRelative(offsetX, offsetY)
        }
    }
}
