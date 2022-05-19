package eu.kanade.tachiyomi.ui.browse.extension

import android.os.Bundle
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
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

class ExtensionFilterPresenter(
    private val getExtensionLanguages: GetExtensionLanguages = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<ExtensionFilterController>() {

    private val _state: MutableStateFlow<ExtensionFilterState> = MutableStateFlow(ExtensionFilterState.Loading)
    val state: StateFlow<ExtensionFilterState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getExtensionLanguages.subscribe()
                .catch { exception ->
                    _state.value = ExtensionFilterState.Error(exception)
                }
                .collectLatest(::collectLatestSourceLangMap)
        }
    }

    private fun collectLatestSourceLangMap(extLangs: List<String>) {
        val enabledLanguages = preferences.enabledLanguages().get()
        val uiModels = extLangs.map {
            FilterUiModel(it, it in enabledLanguages)
        }
        _state.value = ExtensionFilterState.Success(uiModels)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }
}

sealed class ExtensionFilterState {
    object Loading : ExtensionFilterState()
    data class Error(val error: Throwable) : ExtensionFilterState()
    data class Success(val models: List<FilterUiModel>) : ExtensionFilterState()
}
