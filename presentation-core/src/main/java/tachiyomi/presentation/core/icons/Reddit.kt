package tachiyomi.presentation.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("UnusedReceiverParameter", "BooleanLiteralArgument")
val CustomIcons.Reddit: ImageVector
    get() {
        if (_reddit != null) {
            return _reddit!!
        }
        _reddit = Builder(
            name = "Reddit",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(12.0f, 0.0f)
                curveTo(5.373f, 0.0f, 0.0f, 5.373f, 0.0f, 12.0f)
                curveToRelative(0.0f, 3.314f, 1.343f, 6.314f, 3.515f, 8.485f)
                lineToRelative(-2.286f, 2.286f)
                curveTo(0.775f, 23.225f, 1.097f, 24.0f, 1.738f, 24.0f)
                lineTo(12.0f, 24.0f)
                curveToRelative(6.627f, 0.0f, 12.0f, -5.373f, 12.0f, -12.0f)
                reflectiveCurveTo(18.627f, 0.0f, 12.0f, 0.0f)
                close()
                moveTo(16.388f, 3.199f)
                curveToRelative(1.104f, 0.0f, 1.999f, 0.895f, 1.999f, 1.999f)
                curveToRelative(0.0f, 1.105f, -0.895f, 2.0f, -1.999f, 2.0f)
                curveToRelative(-0.946f, 0.0f, -1.739f, -0.657f, -1.947f, -1.539f)
                verticalLineToRelative(0.002f)
                curveToRelative(-1.147f, 0.162f, -2.032f, 1.15f, -2.032f, 2.341f)
                verticalLineToRelative(0.007f)
                curveToRelative(1.776f, 0.067f, 3.4f, 0.567f, 4.686f, 1.363f)
                curveToRelative(0.473f, -0.363f, 1.064f, -0.58f, 1.707f, -0.58f)
                curveToRelative(1.547f, 0.0f, 2.802f, 1.254f, 2.802f, 2.802f)
                curveToRelative(0.0f, 1.117f, -0.655f, 2.081f, -1.601f, 2.531f)
                curveToRelative(-0.088f, 3.256f, -3.637f, 5.876f, -7.997f, 5.876f)
                curveToRelative(-4.361f, 0.0f, -7.905f, -2.617f, -7.998f, -5.87f)
                curveToRelative(-0.954f, -0.447f, -1.614f, -1.415f, -1.614f, -2.538f)
                curveToRelative(0.0f, -1.548f, 1.255f, -2.802f, 2.803f, -2.802f)
                curveToRelative(0.645f, 0.0f, 1.239f, 0.218f, 1.712f, 0.585f)
                curveToRelative(1.275f, -0.79f, 2.881f, -1.291f, 4.64f, -1.365f)
                verticalLineToRelative(-0.01f)
                curveToRelative(0.0f, -1.663f, 1.263f, -3.034f, 2.88f, -3.207f)
                curveToRelative(0.188f, -0.911f, 0.993f, -1.595f, 1.959f, -1.595f)
                close()
                moveTo(8.303f, 11.575f)
                curveToRelative(-0.784f, 0.0f, -1.459f, 0.78f, -1.506f, 1.797f)
                curveToRelative(-0.047f, 1.016f, 0.64f, 1.429f, 1.426f, 1.429f)
                curveToRelative(0.786f, 0.0f, 1.371f, -0.369f, 1.418f, -1.385f)
                curveToRelative(0.047f, -1.017f, -0.553f, -1.841f, -1.338f, -1.841f)
                close()
                moveTo(15.709f, 11.575f)
                curveToRelative(-0.786f, 0.0f, -1.385f, 0.824f, -1.338f, 1.841f)
                curveToRelative(0.047f, 1.017f, 0.634f, 1.385f, 1.418f, 1.385f)
                curveToRelative(0.785f, 0.0f, 1.473f, -0.413f, 1.426f, -1.429f)
                curveToRelative(-0.046f, -1.017f, -0.721f, -1.797f, -1.506f, -1.797f)
                close()
                moveTo(12.006f, 15.588f)
                curveToRelative(-0.974f, 0.0f, -1.907f, 0.048f, -2.77f, 0.135f)
                curveToRelative(-0.147f, 0.015f, -0.241f, 0.168f, -0.183f, 0.305f)
                curveToRelative(0.483f, 1.154f, 1.622f, 1.964f, 2.953f, 1.964f)
                curveToRelative(1.33f, 0.0f, 2.47f, -0.81f, 2.953f, -1.964f)
                curveToRelative(0.057f, -0.137f, -0.037f, -0.29f, -0.184f, -0.305f)
                curveToRelative(-0.863f, -0.087f, -1.795f, -0.135f, -2.769f, -0.135f)
                close()
            }
        }
            .build()
        return _reddit!!
    }

@Suppress("ObjectPropertyName")
private var _reddit: ImageVector? = null
