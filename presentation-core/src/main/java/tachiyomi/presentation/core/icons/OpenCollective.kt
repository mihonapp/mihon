package tachiyomi.presentation.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("UnusedReceiverParameter")
val CustomIcons.OpenCollective: ImageVector
    get() {
        if (_OpenCollective != null) {
            return _OpenCollective!!
        }
        _OpenCollective = ImageVector.Builder(
            name = "OpenCollective",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 0f)
                curveTo(5.373f, 0f, 0f, 5.373f, 0f, 12f)
                reflectiveCurveToRelative(5.373f, 12f, 12f, 12f)
                curveToRelative(2.54f, 0f, 4.894f, -0.79f, 6.834f, -2.135f)
                lineToRelative(-3.107f, -3.109f)
                arcToRelative(7.715f, 7.715f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -13.512f)
                lineToRelative(3.107f, -3.109f)
                arcTo(11.943f, 11.943f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 0f)
                close()
                moveTo(21.865f, 5.166f)
                lineToRelative(-3.109f, 3.107f)
                arcTo(7.67f, 7.67f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.715f, 12f)
                arcToRelative(7.682f, 7.682f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.959f, 3.727f)
                lineToRelative(3.109f, 3.107f)
                arcTo(11.943f, 11.943f, 0f, isMoreThanHalf = false, isPositiveArc = false, 24f, 12f)
                curveToRelative(0f, -2.54f, -0.79f, -4.894f, -2.135f, -6.834f)
                close()
            }
        }.build()

        return _OpenCollective!!
    }

@Suppress("ObjectPropertyName")
private var _OpenCollective: ImageVector? = null
