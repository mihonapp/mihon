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

val CustomIcons.X: ImageVector
    get() {
        if (_x != null) {
            return _x!!
        }
        _x = Builder(
            name = "X", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth =
            24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(18.901f, 1.153f)
                horizontalLineToRelative(3.68f)
                lineToRelative(-8.04f, 9.19f)
                lineTo(24.0f, 22.846f)
                horizontalLineToRelative(-7.406f)
                lineToRelative(-5.8f, -7.584f)
                lineToRelative(-6.638f, 7.584f)
                horizontalLineTo(0.474f)
                lineToRelative(8.6f, -9.83f)
                lineTo(0.0f, 1.154f)
                horizontalLineToRelative(7.594f)
                lineToRelative(5.243f, 6.932f)
                close()
                moveTo(17.61f, 20.644f)
                horizontalLineToRelative(2.039f)
                lineTo(6.486f, 3.24f)
                horizontalLineTo(4.298f)
                close()
            }
        }
            .build()
        return _x!!
    }

private var _x: ImageVector? = null
