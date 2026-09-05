package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ExtensionFilterViewModel(
    private val preferences: SourcePreferences,
    private val getExtensionLanguages: GetExtensionLanguages,
    private val toggleLanguage: ToggleLanguage,
) : ViewModel() {

    private val _events: Channel<ExtensionFilterEvent> = Channel()
    val events: Flow<ExtensionFilterEvent> = _events.receiveAsFlow()

    val state: StateFlow<ExtensionFilterState> = combine(
        getExtensionLanguages.subscribe(),
        preferences.enabledLanguages.changes(),
    ) { extensionLanguages, enabledLanguages ->
        ExtensionFilterState.Success(
            languages = extensionLanguages,
            enabledLanguages = enabledLanguages,
        )
    }
        .catch { throwable ->
            logcat(LogPriority.ERROR, throwable)
            _events.send(ExtensionFilterEvent.FailedFetchingLanguages)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), ExtensionFilterState.Loading)

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface ExtensionFilterEvent {
    data object FailedFetchingLanguages : ExtensionFilterEvent
}

sealed interface ExtensionFilterState {

    @Immutable
    data object Loading : ExtensionFilterState

    @Immutable
    data class Success(
        val languages: List<String>,
        val enabledLanguages: Set<String> = setOf(),
    ) : ExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
