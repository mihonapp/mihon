package eu.kanade.tachiyomi.ui.setting.search

import android.os.Bundle
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsSearchPresenter(
    private val preferences: PreferencesHelper = Injekt.get(),
) : BasePresenter<SettingsSearchController>() {

    private val _state: MutableStateFlow<List<SettingsSearchHelper.SettingsSearchResult>> =
        MutableStateFlow(emptyList())
    val state: StateFlow<List<SettingsSearchHelper.SettingsSearchResult>> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        SettingsSearchHelper.initPreferenceSearchResults(preferences.context)
    }

    fun getLastSearchQuerySearchSettings(): String {
        return preferences.lastSearchQuerySearchSettings().get()
    }

    fun setLastSearchQuerySearchSettings(query: String) {
        preferences.lastSearchQuerySearchSettings().set(query)
    }

    fun searchSettings(query: String?) {
        _state.value = if (!query.isNullOrBlank()) {
            SettingsSearchHelper.getFilteredResults(query)
        } else {
            emptyList()
        }
    }
}
