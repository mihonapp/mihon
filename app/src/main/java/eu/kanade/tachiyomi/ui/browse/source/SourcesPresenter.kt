package eu.kanade.tachiyomi.ui.browse.source

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.browse.SourcesState
import eu.kanade.presentation.browse.SourcesStateImpl
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class SourcesPresenter(
    private val presenterScope: CoroutineScope,
    private val state: SourcesStateImpl = SourcesState() as SourcesStateImpl,
    private val preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
) : SourcesState by state {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    fun onCreate() {
        presenterScope.launchIO {
            getEnabledSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingSources)
                }
                .onStart { delay(500) } // Defer to avoid crashing on initial render
                .collectLatest(::collectLatestSources)
        }
    }

    private fun collectLatestSources(sources: List<Source>) {
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources.groupByTo(map) {
            when {
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }

        val uiModels = byLang.flatMap {
            listOf(
                SourceUiModel.Header(it.key),
                *it.value.map { source ->
                    SourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }
        state.isLoading = false
        state.items = uiModels
    }

    fun onOpenSource(source: Source) {
        if (!preferences.incognitoMode().get()) {
            sourcePreferences.lastUsedSource().set(source.id)
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    sealed class Event {
        object FailedFetchingSources : Event()
    }

    data class Dialog(val source: Source)

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}
