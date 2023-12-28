package tachiyomi.presentation.widget

import android.annotation.SuppressLint
import androidx.compose.ui.unit.dp
import androidx.glance.ImageProvider
import androidx.glance.unit.ColorProvider

class UpdatesGridGlanceWidget : BaseUpdatesGridGlanceWidget() {
    @SuppressLint("RestrictedApi")
    override val foreground = ColorProvider(R.color.appwidget_on_secondary_container)
    override val background = ImageProvider(R.drawable.appwidget_background)
    override val topPadding = 0.dp
    override val bottomPadding = 0.dp
}
