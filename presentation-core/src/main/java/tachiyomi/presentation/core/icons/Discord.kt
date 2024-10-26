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
val CustomIcons.Discord: ImageVector
    get() {
        if (_discord != null) {
            return _discord!!
        }
        _discord = Builder(
            name = "Discord",
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
                moveTo(20.317f, 4.3698f)
                arcToRelative(19.7913f, 19.7913f, 0.0f, false, false, -4.8851f, -1.5152f)
                arcToRelative(0.0741f, 0.0741f, 0.0f, false, false, -0.0785f, 0.0371f)
                curveToRelative(-0.211f, 0.3753f, -0.4447f, 0.8648f, -0.6083f, 1.2495f)
                curveToRelative(-1.8447f, -0.2762f, -3.68f, -0.2762f, -5.4868f, 0.0f)
                curveToRelative(-0.1636f, -0.3933f, -0.4058f, -0.8742f, -0.6177f, -1.2495f)
                arcToRelative(0.077f, 0.077f, 0.0f, false, false, -0.0785f, -0.037f)
                arcToRelative(19.7363f, 19.7363f, 0.0f, false, false, -4.8852f, 1.515f)
                arcToRelative(0.0699f, 0.0699f, 0.0f, false, false, -0.0321f, 0.0277f)
                curveTo(0.5334f, 9.0458f, -0.319f, 13.5799f, 0.0992f, 18.0578f)
                arcToRelative(0.0824f, 0.0824f, 0.0f, false, false, 0.0312f, 0.0561f)
                curveToRelative(2.0528f, 1.5076f, 4.0413f, 2.4228f, 5.9929f, 3.0294f)
                arcToRelative(0.0777f, 0.0777f, 0.0f, false, false, 0.0842f, -0.0276f)
                curveToRelative(0.4616f, -0.6304f, 0.8731f, -1.2952f, 1.226f, -1.9942f)
                arcToRelative(0.076f, 0.076f, 0.0f, false, false, -0.0416f, -0.1057f)
                curveToRelative(-0.6528f, -0.2476f, -1.2743f, -0.5495f, -1.8722f, -0.8923f)
                arcToRelative(0.077f, 0.077f, 0.0f, false, true, -0.0076f, -0.1277f)
                curveToRelative(0.1258f, -0.0943f, 0.2517f, -0.1923f, 0.3718f, -0.2914f)
                arcToRelative(0.0743f, 0.0743f, 0.0f, false, true, 0.0776f, -0.0105f)
                curveToRelative(3.9278f, 1.7933f, 8.18f, 1.7933f, 12.0614f, 0.0f)
                arcToRelative(0.0739f, 0.0739f, 0.0f, false, true, 0.0785f, 0.0095f)
                curveToRelative(0.1202f, 0.099f, 0.246f, 0.1981f, 0.3728f, 0.2924f)
                arcToRelative(0.077f, 0.077f, 0.0f, false, true, -0.0066f, 0.1276f)
                arcToRelative(12.2986f, 12.2986f, 0.0f, false, true, -1.873f, 0.8914f)
                arcToRelative(0.0766f, 0.0766f, 0.0f, false, false, -0.0407f, 0.1067f)
                curveToRelative(0.3604f, 0.698f, 0.7719f, 1.3628f, 1.225f, 1.9932f)
                arcToRelative(0.076f, 0.076f, 0.0f, false, false, 0.0842f, 0.0286f)
                curveToRelative(1.961f, -0.6067f, 3.9495f, -1.5219f, 6.0023f, -3.0294f)
                arcToRelative(0.077f, 0.077f, 0.0f, false, false, 0.0313f, -0.0552f)
                curveToRelative(0.5004f, -5.177f, -0.8382f, -9.6739f, -3.5485f, -13.6604f)
                arcToRelative(0.061f, 0.061f, 0.0f, false, false, -0.0312f, -0.0286f)
                close()
                moveTo(8.02f, 15.3312f)
                curveToRelative(-1.1825f, 0.0f, -2.1569f, -1.0857f, -2.1569f, -2.419f)
                curveToRelative(0.0f, -1.3332f, 0.9555f, -2.4189f, 2.157f, -2.4189f)
                curveToRelative(1.2108f, 0.0f, 2.1757f, 1.0952f, 2.1568f, 2.419f)
                curveToRelative(0.0f, 1.3332f, -0.9555f, 2.4189f, -2.1569f, 2.4189f)
                close()
                moveTo(15.9948f, 15.3312f)
                curveToRelative(-1.1825f, 0.0f, -2.1569f, -1.0857f, -2.1569f, -2.419f)
                curveToRelative(0.0f, -1.3332f, 0.9554f, -2.4189f, 2.1569f, -2.4189f)
                curveToRelative(1.2108f, 0.0f, 2.1757f, 1.0952f, 2.1568f, 2.419f)
                curveToRelative(0.0f, 1.3332f, -0.946f, 2.4189f, -2.1568f, 2.4189f)
                close()
            }
        }
            .build()
        return _discord!!
    }

@Suppress("ObjectPropertyName")
private var _discord: ImageVector? = null
