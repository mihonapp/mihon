package tachiyomi.presentation.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.ImageProvider
import androidx.glance.unit.ColorProvider

class UpdatesGridCoverScreenGlanceWidget : BaseUpdatesGridGlanceWidget() {
    override val foreground = ColorProvider(Color.White)
    override val background = ImageProvider(R.drawable.appwidget_coverscreen_background)
    override val topPadding = 0.dp
    override val bottomPadding = 24.dp
}
