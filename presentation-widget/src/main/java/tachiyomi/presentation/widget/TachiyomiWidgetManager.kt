package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.data.DatabaseHandler

object TachiyomiWidgetManager {

    fun Context.init(scope: LifecycleCoroutineScope, database: DatabaseHandler) {
        database.subscribeToList { updatesViewQueries.updates(after = UpdatesGridGlanceWidget.DateLimit.timeInMillis) }
            .drop(1)
            .distinctUntilChanged()
            .onEach {
                val manager = GlanceAppWidgetManager(this)
                if (manager.getGlanceIds(UpdatesGridGlanceWidget::class.java).isNotEmpty()) {
                    UpdatesGridGlanceWidget().loadData(it)
                }
            }
            .launchIn(scope)
    }
}
