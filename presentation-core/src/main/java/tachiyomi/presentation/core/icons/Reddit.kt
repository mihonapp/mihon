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
                arcTo(12.0f, 12.0f, 0.0f, false, false, 0.0f, 12.0f)
                arcToRelative(12.0f, 12.0f, 0.0f, false, false, 12.0f, 12.0f)
                arcToRelative(12.0f, 12.0f, 0.0f, false, false, 12.0f, -12.0f)
                arcTo(12.0f, 12.0f, 0.0f, false, false, 12.0f, 0.0f)
                close()
                moveTo(17.01f, 4.744f)
                curveToRelative(0.688f, 0.0f, 1.25f, 0.561f, 1.25f, 1.249f)
                arcToRelative(1.25f, 1.25f, 0.0f, false, true, -2.498f, 0.056f)
                lineToRelative(-2.597f, -0.547f)
                lineToRelative(-0.8f, 3.747f)
                curveToRelative(1.824f, 0.07f, 3.48f, 0.632f, 4.674f, 1.488f)
                curveToRelative(0.308f, -0.309f, 0.73f, -0.491f, 1.207f, -0.491f)
                curveToRelative(0.968f, 0.0f, 1.754f, 0.786f, 1.754f, 1.754f)
                curveToRelative(0.0f, 0.716f, -0.435f, 1.333f, -1.01f, 1.614f)
                arcToRelative(3.111f, 3.111f, 0.0f, false, true, 0.042f, 0.52f)
                curveToRelative(0.0f, 2.694f, -3.13f, 4.87f, -7.004f, 4.87f)
                curveToRelative(-3.874f, 0.0f, -7.004f, -2.176f, -7.004f, -4.87f)
                curveToRelative(0.0f, -0.183f, 0.015f, -0.366f, 0.043f, -0.534f)
                arcTo(1.748f, 1.748f, 0.0f, false, true, 4.028f, 12.0f)
                curveToRelative(0.0f, -0.968f, 0.786f, -1.754f, 1.754f, -1.754f)
                curveToRelative(0.463f, 0.0f, 0.898f, 0.196f, 1.207f, 0.49f)
                curveToRelative(1.207f, -0.883f, 2.878f, -1.43f, 4.744f, -1.487f)
                lineToRelative(0.885f, -4.182f)
                arcToRelative(0.342f, 0.342f, 0.0f, false, true, 0.14f, -0.197f)
                arcToRelative(0.35f, 0.35f, 0.0f, false, true, 0.238f, -0.042f)
                lineToRelative(2.906f, 0.617f)
                arcToRelative(1.214f, 1.214f, 0.0f, false, true, 1.108f, -0.701f)
                close()
                moveTo(9.25f, 12.0f)
                curveTo(8.561f, 12.0f, 8.0f, 12.562f, 8.0f, 13.25f)
                curveToRelative(0.0f, 0.687f, 0.561f, 1.248f, 1.25f, 1.248f)
                curveToRelative(0.687f, 0.0f, 1.248f, -0.561f, 1.248f, -1.249f)
                curveToRelative(0.0f, -0.688f, -0.561f, -1.249f, -1.249f, -1.249f)
                close()
                moveTo(14.75f, 12.0f)
                curveToRelative(-0.687f, 0.0f, -1.248f, 0.561f, -1.248f, 1.25f)
                curveToRelative(0.0f, 0.687f, 0.561f, 1.248f, 1.249f, 1.248f)
                curveToRelative(0.688f, 0.0f, 1.249f, -0.561f, 1.249f, -1.249f)
                curveToRelative(0.0f, -0.687f, -0.562f, -1.249f, -1.25f, -1.249f)
                close()
                moveTo(9.284f, 15.99f)
                arcToRelative(0.327f, 0.327f, 0.0f, false, false, -0.231f, 0.094f)
                arcToRelative(0.33f, 0.33f, 0.0f, false, false, 0.0f, 0.463f)
                curveToRelative(0.842f, 0.842f, 2.484f, 0.913f, 2.961f, 0.913f)
                curveToRelative(0.477f, 0.0f, 2.105f, -0.056f, 2.961f, -0.913f)
                arcToRelative(0.361f, 0.361f, 0.0f, false, false, 0.029f, -0.463f)
                arcToRelative(0.33f, 0.33f, 0.0f, false, false, -0.464f, 0.0f)
                curveToRelative(-0.547f, 0.533f, -1.684f, 0.73f, -2.512f, 0.73f)
                curveToRelative(-0.828f, 0.0f, -1.979f, -0.196f, -2.512f, -0.73f)
                arcToRelative(0.326f, 0.326f, 0.0f, false, false, -0.232f, -0.095f)
                close()
            }
        }
            .build()
        return _reddit!!
    }

@Suppress("ObjectPropertyName")
private var _reddit: ImageVector? = null
