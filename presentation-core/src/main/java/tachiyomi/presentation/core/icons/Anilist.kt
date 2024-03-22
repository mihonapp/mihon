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

val CustomIcons.Anilist: ImageVector
    get() {
        if (_anilist != null) {
            return _anilist!!
        }
        _anilist = Builder(
            name = "Anilist", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(6.361f, 2.943f)
                lineTo(0.0f, 21.056f)
                horizontalLineToRelative(4.942f)
                lineToRelative(1.077f, -3.133f)
                lineTo(11.4f, 17.923f)
                lineToRelative(1.052f, 3.133f)
                lineTo(22.9f, 21.056f)
                curveToRelative(0.71f, 0.0f, 1.1f, -0.392f, 1.1f, -1.101f)
                lineTo(24.0f, 17.53f)
                curveToRelative(0.0f, -0.71f, -0.39f, -1.101f, -1.1f, -1.101f)
                horizontalLineToRelative(-6.483f)
                lineTo(16.417f, 4.045f)
                curveToRelative(0.0f, -0.71f, -0.392f, -1.102f, -1.101f, -1.102f)
                horizontalLineToRelative(-2.422f)
                curveToRelative(-0.71f, 0.0f, -1.101f, 0.392f, -1.101f, 1.102f)
                verticalLineToRelative(1.064f)
                lineToRelative(-0.758f, -2.166f)
                close()
                moveTo(8.685f, 8.891f)
                lineTo(10.373f, 13.909f)
                lineTo(7.144f, 13.909f)
                close()
            }
        }
            .build()
        return _anilist!!
    }

private var _anilist: ImageVector? = null
