package eu.kanade.tachiyomi.ui.browse.source

import android.os.Bundle
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourcesFilterPresenter(
    private val getLanguagesWithSources: GetLanguagesWithSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<SourceFilterController>() {

    private val _state: MutableStateFlow<SourceFilterState> = MutableStateFlow(SourceFilterState.Loading)
    val state: StateFlow<SourceFilterState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            getLanguagesWithSources.subscribe()
                .catch { exception ->
                    _state.value = SourceFilterState.Error(exception)
                }
                .collectLatest(::collectLatestSourceLangMap)
        }
    }

    private fun collectLatestSourceLangMap(sourceLangMap: Map<String, List<Source>>) {
        val uiModels = sourceLangMap.flatMap {
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
        _state.value = SourceFilterState.Success(uiModels)
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }
}

sealed class SourceFilterState {
    object Loading : SourceFilterState()
    data class Error(val error: Throwable) : SourceFilterState()
    data class Success(val models: List<FilterUiModel>) : SourceFilterState()
}
