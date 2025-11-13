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

@Suppress("UnusedReceiverParameter")
val CustomIcons.Facebook: ImageVector
    get() {
        if (_facebook != null) {
            return _facebook!!
        }
        _facebook = Builder(
            name = "Facebook",
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
                moveTo(9.101f, 23.691f)
                verticalLineToRelative(-7.98f)
                horizontalLineTo(6.627f)
                verticalLineToRelative(-3.667f)
                horizontalLineToRelative(2.474f)
                verticalLineToRelative(-1.58f)
                curveToRelative(0.0f, -4.085f, 1.848f, -5.978f, 5.858f, -5.978f)
                curveToRelative(0.401f, 0.0f, 0.955f, 0.042f, 1.468f, 0.103f)
                arcToRelative(8.68f, 8.68f, 0.0f, false, true, 1.141f, 0.195f)
                verticalLineToRelative(3.325f)
                arcToRelative(8.623f, 8.623f, 0.0f, false, false, -0.653f, -0.036f)
                arcToRelative(26.805f, 26.805f, 0.0f, false, false, -0.733f, -0.009f)
                curveToRelative(-0.707f, 0.0f, -1.259f, 0.096f, -1.675f, 0.309f)
                arcToRelative(1.686f, 1.686f, 0.0f, false, false, -0.679f, 0.622f)
                curveToRelative(-0.258f, 0.42f, -0.374f, 0.995f, -0.374f, 1.752f)
                verticalLineToRelative(1.297f)
                horizontalLineToRelative(3.919f)
                lineToRelative(-0.386f, 2.103f)
                lineToRelative(-0.287f, 1.564f)
                horizontalLineToRelative(-3.246f)
                verticalLineToRelative(8.245f)
                curveTo(19.396f, 23.238f, 24.0f, 18.179f, 24.0f, 12.044f)
                curveToRelative(0.0f, -6.627f, -5.373f, -12.0f, -12.0f, -12.0f)
                reflectiveCurveToRelative(-12.0f, 5.373f, -12.0f, 12.0f)
                curveToRelative(0.0f, 5.628f, 3.874f, 10.35f, 9.101f, 11.647f)
                close()
            }
        }
            .build()
        return _facebook!!
    }

@Suppress("ObjectPropertyName")
private var _facebook: ImageVector? = null
