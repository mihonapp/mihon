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

val CustomIcons.Facebook: ImageVector
    get() {
        if (_facebook != null) {
            return _facebook!!
        }
        _facebook = Builder(
            name = "Facebook", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(24.0f, 12.073f)
                curveToRelative(0.0f, -6.627f, -5.373f, -12.0f, -12.0f, -12.0f)
                reflectiveCurveToRelative(-12.0f, 5.373f, -12.0f, 12.0f)
                curveToRelative(0.0f, 5.99f, 4.388f, 10.954f, 10.125f, 11.854f)
                verticalLineToRelative(-8.385f)
                horizontalLineTo(7.078f)
                verticalLineToRelative(-3.47f)
                horizontalLineToRelative(3.047f)
                verticalLineTo(9.43f)
                curveToRelative(0.0f, -3.007f, 1.792f, -4.669f, 4.533f, -4.669f)
                curveToRelative(1.312f, 0.0f, 2.686f, 0.235f, 2.686f, 0.235f)
                verticalLineToRelative(2.953f)
                horizontalLineTo(15.83f)
                curveToRelative(-1.491f, 0.0f, -1.956f, 0.925f, -1.956f, 1.874f)
                verticalLineToRelative(2.25f)
                horizontalLineToRelative(3.328f)
                lineToRelative(-0.532f, 3.47f)
                horizontalLineToRelative(-2.796f)
                verticalLineToRelative(8.385f)
                curveTo(19.612f, 23.027f, 24.0f, 18.062f, 24.0f, 12.073f)
                close()
            }
        }
            .build()
        return _facebook!!
    }

private var _facebook: ImageVector? = null
