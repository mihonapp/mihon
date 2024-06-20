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

val CustomIcons.Kitsu: ImageVector
    get() {
        if (_kitsu != null) {
            return _kitsu!!
        }
        _kitsu = Builder(
            name = "Kitsu", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(1.429f, 5.441f)
                arcToRelative(12.478f, 12.478f, 0.0f, false, false, 1.916f, 2.056f)
                curveToRelative(0.011f, 0.011f, 0.022f, 0.011f, 0.022f, 0.022f)
                curveToRelative(0.452f, 0.387f, 1.313f, 0.947f, 1.937f, 1.173f)
                curveToRelative(0.0f, 0.0f, 3.886f, 1.496f, 4.091f, 1.582f)
                arcToRelative(1.4f, 1.4f, 0.0f, false, false, 0.237f, 0.075f)
                arcToRelative(0.694f, 0.694f, 0.0f, false, false, 0.808f, -0.549f)
                curveToRelative(0.011f, -0.065f, 0.022f, -0.172f, 0.022f, -0.248f)
                lineTo(10.462f, 5.161f)
                curveToRelative(0.011f, -0.667f, -0.205f, -1.679f, -0.398f, -2.239f)
                curveToRelative(0.0f, -0.011f, -0.011f, -0.022f, -0.011f, -0.032f)
                arcTo(11.979f, 11.979f, 0.0f, false, false, 8.824f, 0.36f)
                lineTo(8.781f, 0.285f)
                arcToRelative(0.697f, 0.697f, 0.0f, false, false, -0.958f, -0.162f)
                curveToRelative(-0.054f, 0.032f, -0.086f, 0.075f, -0.129f, 0.119f)
                lineTo(7.608f, 0.36f)
                arcToRelative(4.743f, 4.743f, 0.0f, false, false, -0.786f, 3.412f)
                arcToRelative(8.212f, 8.212f, 0.0f, false, false, -0.775f, 0.463f)
                curveToRelative(-0.043f, 0.032f, -0.42f, 0.291f, -0.71f, 0.56f)
                arcTo(4.803f, 4.803f, 0.0f, false, false, 1.87f, 4.3f)
                curveToRelative(-0.043f, 0.011f, -0.097f, 0.021f, -0.14f, 0.032f)
                curveToRelative(-0.054f, 0.022f, -0.107f, 0.043f, -0.151f, 0.076f)
                arcToRelative(0.702f, 0.702f, 0.0f, false, false, -0.193f, 0.958f)
                lineToRelative(0.043f, 0.075f)
                close()
                moveTo(8.222f, 1.07f)
                curveToRelative(0.366f, 0.614f, 0.678f, 1.249f, 0.925f, 1.917f)
                curveToRelative(-0.495f, 0.086f, -0.98f, 0.215f, -1.453f, 0.388f)
                arcToRelative(3.918f, 3.918f, 0.0f, false, true, 0.528f, -2.305f)
                close()
                moveTo(4.658f, 5.463f)
                arcToRelative(7.467f, 7.467f, 0.0f, false, false, -0.893f, 1.216f)
                arcToRelative(11.68f, 11.68f, 0.0f, false, true, -1.453f, -1.55f)
                arcToRelative(3.825f, 3.825f, 0.0f, false, true, 2.346f, 0.334f)
                close()
                moveTo(17.706f, 5.161f)
                arcToRelative(7.673f, 7.673f, 0.0f, false, false, -2.347f, -0.474f)
                arcToRelative(7.583f, 7.583f, 0.0f, false, false, -3.811f, 0.818f)
                lineToRelative(-0.215f, 0.108f)
                verticalLineToRelative(3.918f)
                curveToRelative(0.0f, 0.054f, 0.0f, 0.258f, -0.032f, 0.431f)
                arcToRelative(1.535f, 1.535f, 0.0f, false, true, -0.646f, 0.98f)
                arcToRelative(1.545f, 1.545f, 0.0f, false, true, -1.152f, 0.247f)
                arcToRelative(2.618f, 2.618f, 0.0f, false, true, -0.409f, -0.118f)
                arcToRelative(747.6f, 747.6f, 0.0f, false, true, -3.402f, -1.313f)
                arcToRelative(8.9f, 8.9f, 0.0f, false, false, -0.323f, -0.129f)
                arcToRelative(30.597f, 30.597f, 0.0f, false, false, -3.822f, 3.832f)
                lineToRelative(-0.075f, 0.086f)
                arcToRelative(0.698f, 0.698f, 0.0f, false, false, 0.538f, 1.098f)
                arcToRelative(0.676f, 0.676f, 0.0f, false, false, 0.42f, -0.118f)
                curveToRelative(0.011f, -0.011f, 0.022f, -0.022f, 0.043f, -0.032f)
                curveToRelative(1.313f, -0.947f, 2.756f, -1.712f, 4.284f, -2.325f)
                arcToRelative(0.7f, 0.7f, 0.0f, false, true, 0.818f, 0.13f)
                arcToRelative(0.704f, 0.704f, 0.0f, false, true, 0.054f, 0.915f)
                lineToRelative(-0.237f, 0.388f)
                arcToRelative(20.277f, 20.277f, 0.0f, false, false, -1.97f, 4.306f)
                lineToRelative(-0.032f, 0.129f)
                arcToRelative(0.646f, 0.646f, 0.0f, false, false, 0.108f, 0.538f)
                arcToRelative(0.713f, 0.713f, 0.0f, false, false, 0.549f, 0.301f)
                arcToRelative(0.657f, 0.657f, 0.0f, false, false, 0.42f, -0.118f)
                curveToRelative(0.054f, -0.043f, 0.108f, -0.086f, 0.151f, -0.14f)
                lineToRelative(0.043f, -0.065f)
                arcToRelative(18.95f, 18.95f, 0.0f, false, true, 1.765f, -2.153f)
                arcToRelative(20.156f, 20.156f, 0.0f, false, true, 10.797f, -6.018f)
                curveToRelative(0.032f, -0.011f, 0.065f, -0.011f, 0.097f, -0.011f)
                curveToRelative(0.237f, 0.011f, 0.42f, 0.215f, 0.409f, 0.452f)
                arcToRelative(0.424f, 0.424f, 0.0f, false, true, -0.344f, 0.398f)
                curveToRelative(-3.908f, 0.829f, -10.948f, 5.469f, -8.483f, 12.208f)
                curveToRelative(0.043f, 0.108f, 0.075f, 0.172f, 0.129f, 0.269f)
                arcToRelative(0.71f, 0.71f, 0.0f, false, false, 0.538f, 0.301f)
                arcToRelative(0.742f, 0.742f, 0.0f, false, false, 0.657f, -0.398f)
                curveToRelative(0.398f, -0.754f, 1.152f, -1.593f, 3.326f, -2.497f)
                curveToRelative(6.061f, -2.508f, 7.062f, -6.093f, 7.17f, -8.364f)
                verticalLineToRelative(-0.129f)
                arcToRelative(7.716f, 7.716f, 0.0f, false, false, -5.016f, -7.451f)
                close()
                moveTo(11.623f, 22.923f)
                curveToRelative(-0.56f, -1.669f, -0.506f, -3.283f, 0.151f, -4.823f)
                curveToRelative(1.26f, 2.035f, 3.456f, 2.207f, 3.456f, 2.207f)
                curveToRelative(-2.25f, 0.937f, -3.133f, 1.863f, -3.607f, 2.616f)
                close()
            }
        }
            .build()
        return _kitsu!!
    }

private var _kitsu: ImageVector? = null
