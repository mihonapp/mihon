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

val CustomIcons.MyAnimeList: ImageVector
    get() {
        if (_myAnimeList != null) {
            return _myAnimeList!!
        }
        _myAnimeList = Builder(
            name = "MyAnimeList", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(8.273f, 7.247f)
                verticalLineToRelative(8.423f)
                lineToRelative(-2.103f, -0.003f)
                verticalLineToRelative(-5.216f)
                lineToRelative(-2.03f, 2.404f)
                lineToRelative(-1.989f, -2.458f)
                lineToRelative(-0.02f, 5.285f)
                lineTo(0.001f, 15.682f)
                lineTo(0.0f, 7.247f)
                horizontalLineToRelative(2.203f)
                lineToRelative(1.865f, 2.545f)
                lineToRelative(2.015f, -2.546f)
                lineToRelative(2.19f, 0.001f)
                close()
                moveTo(16.901f, 9.316f)
                lineToRelative(0.025f, 6.335f)
                horizontalLineToRelative(-2.365f)
                lineToRelative(-0.008f, -2.871f)
                horizontalLineToRelative(-2.8f)
                curveToRelative(0.07f, 0.499f, 0.21f, 1.266f, 0.417f, 1.779f)
                curveToRelative(0.155f, 0.381f, 0.298f, 0.751f, 0.583f, 1.128f)
                lineToRelative(-1.705f, 1.125f)
                curveToRelative(-0.349f, -0.636f, -0.622f, -1.337f, -0.878f, -2.082f)
                arcToRelative(9.296f, 9.296f, 0.0f, false, true, -0.507f, -2.179f)
                curveToRelative(-0.085f, -0.75f, -0.097f, -1.471f, 0.107f, -2.212f)
                arcToRelative(3.908f, 3.908f, 0.0f, false, true, 1.161f, -1.866f)
                curveToRelative(0.313f, -0.293f, 0.749f, -0.5f, 1.1f, -0.687f)
                curveToRelative(0.351f, -0.187f, 0.743f, -0.264f, 1.107f, -0.359f)
                arcToRelative(7.405f, 7.405f, 0.0f, false, true, 1.191f, -0.183f)
                curveToRelative(0.398f, -0.034f, 1.107f, -0.066f, 2.39f, -0.028f)
                lineToRelative(0.545f, 1.749f)
                lineTo(14.51f, 8.965f)
                curveToRelative(-0.593f, 0.008f, -0.878f, 0.001f, -1.341f, 0.209f)
                arcToRelative(2.236f, 2.236f, 0.0f, false, false, -1.278f, 1.92f)
                lineToRelative(2.663f, 0.033f)
                lineToRelative(0.038f, -1.81f)
                horizontalLineToRelative(2.309f)
                close()
                moveTo(20.893f, 7.217f)
                verticalLineToRelative(6.627f)
                lineToRelative(3.107f, 0.032f)
                lineToRelative(-0.43f, 1.775f)
                horizontalLineToRelative(-4.807f)
                lineTo(18.763f, 7.187f)
                lineToRelative(2.13f, 0.03f)
                close()
            }
        }
            .build()
        return _myAnimeList!!
    }

private var _myAnimeList: ImageVector? = null
