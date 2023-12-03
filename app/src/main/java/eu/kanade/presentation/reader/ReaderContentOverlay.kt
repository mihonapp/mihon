package eu.kanade.presentation.reader

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

@Composable
fun ReaderContentOverlay(
    @IntRange(from = -100, to = 100) brightness: Int,
    @ColorInt color: Int?,
    colorBlendMode: BlendMode? = BlendMode.SrcOver,
) {
    if (brightness < 0) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = abs(brightness) / 100f
                },
        ) {
            drawRect(Color.Black)
        }
    }

    if (color != null) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            drawRect(
                color = Color(color),
                blendMode = colorBlendMode,
            )
        }
    }
}
