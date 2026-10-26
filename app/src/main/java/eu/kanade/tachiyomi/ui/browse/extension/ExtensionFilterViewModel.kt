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
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    private val _events: Channel<ExtensionFilterEvent> = Channel()
    val events: Flow<ExtensionFilterEvent> = _events.receiveAsFlow()

    val state: StateFlow<ExtensionFilterState> = combine(
        getExtensionLanguages.subscribe(),
        preferences.enabledLanguages.changes(),
        extensionManager.allAvailableExtensionsFlow,
    ) { extensionLanguages, enabledLanguages, availableExtensions ->
        ExtensionFilterState.Success(
            languages = extensionLanguages,
            enabledLanguages = enabledLanguages,
            // Only the stores that actually returned a listing count as loaded, and their languages are
            // the ones their own extensions are written in
            stores = availableExtensions
                .groupBy { it.store.indexUrl }
                .map { (indexUrl, listings) ->
                    ExtensionFilterState.Success.StoreContents(
                        name = listings.first().store.name,
                        indexUrl = indexUrl,
                        languages = listings
                            .map { it.lang }
                            .distinct()
                            .sortedWith(LocaleHelper.comparator),
                    )
                }
                .sortedBy { it.name.lowercase() },
        )
    }
        .catch { throwable ->
            logcat(LogPriority.ERROR, throwable)
            _events.send(ExtensionFilterEvent.FailedFetchingLanguages)
        }
        .flowOn(Dispatchers.Default)
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
        val stores: List<StoreContents> = emptyList(),
    ) : ExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty() && stores.isEmpty()

        /**
         * A store that returned a listing, with the languages its extensions are available in.
         */
        @Immutable
        data class StoreContents(
            val name: String,
            val indexUrl: String,
            val languages: List<String>,
        )
    }
}
