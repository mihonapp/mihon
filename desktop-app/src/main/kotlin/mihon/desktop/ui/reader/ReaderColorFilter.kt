package mihon.desktop.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderColorFilter

// Invert color matrix: r' = 255 - r, g' = 255 - g, b' = 255 - b
internal val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// Grayscale matrix using standard luminance weights
internal val GrayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }

// Invert + Grayscale matrix
internal val InvertGrayscaleMatrix = ColorMatrix(
    floatArrayOf(
        -0.299f, -0.587f, -0.114f, 0f, 255f,
        -0.299f, -0.587f, -0.114f, 0f, 255f,
        -0.299f, -0.587f, -0.114f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// Sepia eye-care warm tone matrix
internal val SepiaMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// Night mode matrix: softened brightness with warm bias
internal val NightModeMatrix = ColorMatrix(
    floatArrayOf(
        0.75f, 0f, 0f, 0f, 10f,
        0f, 0.70f, 0f, 0f, 10f,
        0f, 0f, 0.60f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

fun ReaderColorFilter.toComposeColorFilter(): ColorFilter? = when (this) {
    ReaderColorFilter.NONE -> null
    ReaderColorFilter.INVERT -> ColorFilter.colorMatrix(InvertMatrix)
    ReaderColorFilter.GRAYSCALE -> ColorFilter.colorMatrix(GrayscaleMatrix)
    ReaderColorFilter.INVERT_GRAYSCALE -> ColorFilter.colorMatrix(InvertGrayscaleMatrix)
    ReaderColorFilter.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
    ReaderColorFilter.NIGHT -> ColorFilter.colorMatrix(NightModeMatrix)
}

fun ReaderBackgroundColor.toComposeColor(): Color = when (this) {
    ReaderBackgroundColor.DARK_GRAY -> Color(0xff101010)
    ReaderBackgroundColor.BLACK -> Color(0xff000000)
    ReaderBackgroundColor.WHITE -> Color(0xffffffff)
    ReaderBackgroundColor.WARM_CREAM -> Color(0xfff5efeb)
}
