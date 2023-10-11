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

val CustomIcons.Github: ImageVector
    get() {
        if (_github != null) {
            return _github!!
        }
        _github = Builder(
            name = "Github", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(12.0f, 0.297f)
                curveToRelative(-6.63f, 0.0f, -12.0f, 5.373f, -12.0f, 12.0f)
                curveToRelative(0.0f, 5.303f, 3.438f, 9.8f, 8.205f, 11.385f)
                curveToRelative(0.6f, 0.113f, 0.82f, -0.258f, 0.82f, -0.577f)
                curveToRelative(0.0f, -0.285f, -0.01f, -1.04f, -0.015f, -2.04f)
                curveToRelative(-3.338f, 0.724f, -4.042f, -1.61f, -4.042f, -1.61f)
                curveTo(4.422f, 18.07f, 3.633f, 17.7f, 3.633f, 17.7f)
                curveToRelative(-1.087f, -0.744f, 0.084f, -0.729f, 0.084f, -0.729f)
                curveToRelative(1.205f, 0.084f, 1.838f, 1.236f, 1.838f, 1.236f)
                curveToRelative(1.07f, 1.835f, 2.809f, 1.305f, 3.495f, 0.998f)
                curveToRelative(0.108f, -0.776f, 0.417f, -1.305f, 0.76f, -1.605f)
                curveToRelative(-2.665f, -0.3f, -5.466f, -1.332f, -5.466f, -5.93f)
                curveToRelative(0.0f, -1.31f, 0.465f, -2.38f, 1.235f, -3.22f)
                curveToRelative(-0.135f, -0.303f, -0.54f, -1.523f, 0.105f, -3.176f)
                curveToRelative(0.0f, 0.0f, 1.005f, -0.322f, 3.3f, 1.23f)
                curveToRelative(0.96f, -0.267f, 1.98f, -0.399f, 3.0f, -0.405f)
                curveToRelative(1.02f, 0.006f, 2.04f, 0.138f, 3.0f, 0.405f)
                curveToRelative(2.28f, -1.552f, 3.285f, -1.23f, 3.285f, -1.23f)
                curveToRelative(0.645f, 1.653f, 0.24f, 2.873f, 0.12f, 3.176f)
                curveToRelative(0.765f, 0.84f, 1.23f, 1.91f, 1.23f, 3.22f)
                curveToRelative(0.0f, 4.61f, -2.805f, 5.625f, -5.475f, 5.92f)
                curveToRelative(0.42f, 0.36f, 0.81f, 1.096f, 0.81f, 2.22f)
                curveToRelative(0.0f, 1.606f, -0.015f, 2.896f, -0.015f, 3.286f)
                curveToRelative(0.0f, 0.315f, 0.21f, 0.69f, 0.825f, 0.57f)
                curveTo(20.565f, 22.092f, 24.0f, 17.592f, 24.0f, 12.297f)
                curveToRelative(0.0f, -6.627f, -5.373f, -12.0f, -12.0f, -12.0f)
            }
        }
            .build()
        return _github!!
    }

private var _github: ImageVector? = null
