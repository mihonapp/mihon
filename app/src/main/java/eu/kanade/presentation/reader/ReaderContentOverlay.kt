package eu.kanade.presentation.reader

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

@Composable
fun ReaderContentOverlay(
    @IntRange(from = -100, to = 100) brightness: Int,
    @ColorInt color: Int?,
    colorBlendMode: BlendMode?,
    modifier: Modifier = Modifier,
) {
    if (brightness < 0) {
        val brightnessAlpha = remember(brightness) {
            abs(brightness) / 100f
        }

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = brightnessAlpha
                },
        ) {
            drawRect(Color.Black)
        }
    }

    if (color != null) {
        Canvas(
            modifier = modifier
                .fillMaxSize(),
        ) {
            drawRect(
                color = Color(color),
                blendMode = colorBlendMode ?: BlendMode.SrcOver,
            )
        }
    }
}
