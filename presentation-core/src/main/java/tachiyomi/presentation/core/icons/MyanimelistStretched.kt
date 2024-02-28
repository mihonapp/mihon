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

public val CustomIcons.MyAnimeListStretched: ImageVector
    get() {
        if (_myAnimeListStretched != null) {
            return _myAnimeListStretched!!
        }
        _myAnimeListStretched = Builder(name = "MyAnimeList Stretched", defaultWidth = 24.0.dp,
                defaultHeight = 18.716352.dp, viewportWidth = 24.0f, viewportHeight =
                18.716352f).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 1.39446f,
                    strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                    pathFillType = NonZero) {
                moveTo(8.273f, 0.1167f)
                lineTo(8.273f, 16.4957f)
                lineTo(6.17f, 16.4898f)
                lineTo(6.17f, 6.347f)
                lineTo(4.14f, 11.0217f)
                lineTo(2.151f, 6.242f)
                lineTo(2.131f, 16.519f)
                lineTo(0.001f, 16.519f)
                lineTo(0.0f, 0.1167f)
                lineTo(2.203f, 0.1167f)
                lineTo(4.068f, 5.0656f)
                lineTo(6.083f, 0.1147f)
                close()
                moveTo(16.901f, 4.14f)
                lineTo(16.926f, 16.4587f)
                horizontalLineToRelative(-2.365f)
                lineToRelative(-0.008f, -5.5828f)
                horizontalLineToRelative(-2.8f)
                curveToRelative(0.07f, 0.9703f, 0.21f, 2.4618f, 0.417f, 3.4594f)
                curveToRelative(0.155f, 0.7409f, 0.298f, 1.4604f, 0.583f, 2.1935f)
                lineToRelative(-1.705f, 2.1876f)
                curveTo(10.699f, 17.4796f, 10.426f, 16.1165f, 10.17f, 14.6678f)
                arcTo(9.296f, 18.0766f, 0.0f, false, true, 9.663f, 10.4306f)
                curveTo(9.578f, 8.9722f, 9.566f, 7.5702f, 9.77f, 6.1292f)
                arcTo(3.908f, 7.5993f, 0.0f, false, true, 10.931f, 2.5007f)
                curveToRelative(0.313f, -0.5698f, 0.749f, -0.9723f, 1.1f, -1.3359f)
                curveToRelative(0.351f, -0.3636f, 0.743f, -0.5134f, 1.107f, -0.6981f)
                arcToRelative(7.405f, 14.3994f, 0.0f, false, true, 1.191f, -0.3559f)
                curveToRelative(0.398f, -0.0661f, 1.107f, -0.1283f, 2.39f, -0.0544f)
                lineTo(17.264f, 3.4574f)
                lineTo(14.51f, 3.4574f)
                curveToRelative(-0.593f, 0.0156f, -0.878f, 0.002f, -1.341f, 0.4064f)
                arcToRelative(2.236f, 4.348f, 0.0f, false, false, -1.278f, 3.7335f)
                lineToRelative(2.663f, 0.0642f)
                lineToRelative(0.038f, -3.5196f)
                horizontalLineToRelative(2.309f)
                close()
                moveTo(20.893f, 0.0583f)
                lineTo(20.893f, 12.9449f)
                lineTo(24.0f, 13.0071f)
                lineTo(23.57f, 16.4587f)
                lineTo(18.763f, 16.4587f)
                lineTo(18.763f, -0.0f)
                close()
            }
        }
        .build()
        return _myAnimeListStretched!!
    }

private var _myAnimeListStretched: ImageVector? = null
