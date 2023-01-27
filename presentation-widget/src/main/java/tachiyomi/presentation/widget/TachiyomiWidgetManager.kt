package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TachiyomiWidgetManager(
    private val database: DatabaseHandler = Injekt.get(),
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
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
