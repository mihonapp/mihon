package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class UpdatesGridCoverScreenGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = UpdatesGridCoverScreenGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetManager.onWidgetEnabled(glanceAppWidget)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetManager.onWidgetDisabled(glanceAppWidget)
    }
}
