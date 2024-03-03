package tachiyomi.presentation.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CustomIcons.Bangumi: ImageVector
    get() {
        if (_bangumi != null) {
            return _bangumi!!
        }
        _bangumi = Builder(
            name = "Bangumi", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 400.0f, viewportHeight = 400.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = SolidColor(Color(0x00000000)),
                strokeLineWidth = 0.0f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = EvenOdd
            ) {
                moveToRelative(171.562f, 42.451f)
                curveToRelative(-14.318f, 4.363f, -15.599f, 6.561f, -93.776f, 160.83f)
                curveToRelative(-58.257f, 114.961f, -59.368f, 118.79f, -38.726f, 133.488f)
                curveToRelative(5.287f, 3.765f, 9.584f, 4.86f, 33.993f, 8.661f)
                curveToRelative(20.337f, 3.168f, 146.161f, 1.97f, 173.954f, -1.656f)
                curveToRelative(49.553f, -6.465f, 53.029f, -9.922f, 94.489f, -94.002f)
                curveToRelative(39.009f, -79.106f, 39.545f, -80.71f, 31.995f, -95.681f)
                curveToRelative(-10.852f, -21.52f, -23.614f, -24.701f, -105.702f, -26.349f)
                curveToRelative(-87.154f, -1.751f, -81.589f, 1.915f, -61.103f, -40.251f)
                curveToRelative(11.499f, -23.669f, 17.38f, -40.221f, 14.998f, -42.214f)
                curveToRelative(-5.58f, -4.67f, -38.063f, -6.501f, -50.122f, -2.826f)
                moveTo(289.281f, 175.86f)
                curveToRelative(12.396f, 5.387f, 12.509f, 5.035f, -24.76f, 76.851f)
                curveToRelative(-28.307f, 54.547f, -25.56f, 53.017f, -93.15f, 51.905f)
                curveToRelative(-77.348f, -1.273f, -76.368f, 3.919f, -19.294f, -102.212f)
                curveToRelative(15.498f, -28.819f, 20.53f, -30.564f, 86.765f, -30.092f)
                curveToRelative(37.501f, 0.268f, 43.933f, 0.72f, 50.439f, 3.548f)
                moveTo(-23.0f, -9.0f)
                horizontalLineToRelative(20.0f)
                verticalLineToRelative(20.0f)
                horizontalLineToRelative(-20.0f)
                close()
                moveTo(407.0f, -9.0f)
                horizontalLineToRelative(20.0f)
                verticalLineToRelative(20.0f)
                horizontalLineToRelative(-20.0f)
                close()
            }
        }
            .build()
        return _bangumi!!
    }

private var _bangumi: ImageVector? = null
