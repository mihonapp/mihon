package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.interactor.GetUpdates

@Inject
class WidgetManager(
    private val getUpdates: GetUpdates,
    private val securityPreferences: SecurityPreferences,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    context(context: Context)
    fun init(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            val manager = GlanceAppWidgetManager(context)
            val placed = widgets.filter { manager.getGlanceIds(it.javaClass).isNotEmpty() }
            placedWidgets.update { it + placed.map { widget -> widget.javaClass } }
        }

        // Only watch updates while a widget is on the home screen
        placedWidgets
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { hasWidgets ->
                if (hasWidgets) {
                    combine(
                        getUpdates.subscribe(
                            read = false,
                            after = BaseUpdatesGridGlanceWidget.DateLimit,
                        ),
                        securityPreferences.useAuthenticator.changes(),
                        transform = { a, b -> a to b },
                    )
                } else {
                    emptyFlow()
                }
            }
            .distinctUntilChanged { old, new ->
                old.second == new.second &&
                    old.first.map { it.chapterId }.toSet() == new.first.map { it.chapterId }.toSet()
            }
            .onEach {
                try {
                    widgets.forEach { it.updateAll(context) }
                } catch (e: Exception) {
                    this.logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }

    companion object {
        private val widgets: List<GlanceAppWidget>
            get() = listOf(UpdatesGridGlanceWidget(), UpdatesGridCoverScreenGlanceWidget())

        private val placedWidgets = MutableStateFlow(emptySet<Class<out GlanceAppWidget>>())

        internal fun onWidgetEnabled(widget: GlanceAppWidget) {
            placedWidgets.update { it + widget.javaClass }
        }

        internal fun onWidgetDisabled(widget: GlanceAppWidget) {
            placedWidgets.update { it - widget.javaClass }
        }
    }
}
