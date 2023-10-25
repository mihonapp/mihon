package eu.kanade.presentation.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

@Composable
fun BrightnessOverlay(
    value: Int,
) {
    if (value >= 0) return

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = abs(value) / 100f
            },
    ) {
        drawRect(Color.Black)
    }
}
