package eu.kanade.tachiyomi.ui.browse.source

import android.os.Bundle
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.SourcesFilterState
import eu.kanade.presentation.browse.SourcesFilterStateImpl
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourcesFilterPresenter(
    private val state: SourcesFilterStateImpl = SourcesFilterState() as SourcesFilterStateImpl,
    private val getLanguagesWithSources: GetLanguagesWithSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : BasePresenter<SourceFilterController>(), SourcesFilterState by state {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            getLanguagesWithSources.subscribe()
                .catch { exception ->
                    logcat(LogPriority.ERROR, exception)
                    _events.send(Event.FailedFetchingLanguages)
                }
                .collectLatest(::collectLatestSourceLangMap)
        }
    }

    private fun collectLatestSourceLangMap(sourceLangMap: Map<String, List<Source>>) {
        state.items = sourceLangMap.flatMap {
            val isLangEnabled = it.key in preferences.enabledLanguages().get()
            val header = listOf(FilterUiModel.Header(it.key, isLangEnabled))

            if (isLangEnabled.not()) return@flatMap header
            header + it.value.map { source ->
                FilterUiModel.Item(
                    source,
                    source.id.toString() !in preferences.disabledSources().get(),
                )
            }
        }
        state.isLoading = false
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    sealed class Event {
        object FailedFetchingLanguages : Event()
    }
}
