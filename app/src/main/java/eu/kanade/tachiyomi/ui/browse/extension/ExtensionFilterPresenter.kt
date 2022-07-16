package eu.kanade.tachiyomi.ui.browse.extension

import android.os.Bundle
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.presentation.browse.ExtensionFilterState
import eu.kanade.presentation.browse.ExtensionFilterStateImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
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

class ExtensionFilterPresenter(
    private val state: ExtensionFilterStateImpl = ExtensionFilterState() as ExtensionFilterStateImpl,
    private val getExtensionLanguages: GetExtensionLanguages = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<ExtensionFilterController>(), ExtensionFilterState by state {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getExtensionLanguages.subscribe()
                .catch { exception ->
                    logcat(LogPriority.ERROR, exception)
                    _events.send(Event.FailedFetchingLanguages)
                }
                .collectLatest(::collectLatestSourceLangMap)
        }
    }

    private fun collectLatestSourceLangMap(extLangs: List<String>) {
        val enabledLanguages = preferences.enabledLanguages().get()
        state.items = extLangs.map {
            FilterUiModel(it, it in enabledLanguages)
        }
        state.isLoading = false
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    sealed class Event {
        object FailedFetchingLanguages : Event()
    }
}
