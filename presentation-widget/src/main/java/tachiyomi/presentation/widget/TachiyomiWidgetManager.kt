package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.updates.interactor.GetUpdates

class TachiyomiWidgetManager(
    private val getUpdates: GetUpdates,
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
        getUpdates.subscribe(
            read = false,
            after = UpdatesGridGlanceWidget.DateLimit.timeInMillis,
        )
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
