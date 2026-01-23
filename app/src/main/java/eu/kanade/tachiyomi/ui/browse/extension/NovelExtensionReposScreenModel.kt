package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.jsplugin.model.JsPluginRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionReposScreenModel(
    private val jsPluginManager: JsPluginManager = Injekt.get(),
) : StateScreenModel<NovelRepoScreenState>(NovelRepoScreenState.Loading) {

    init {
        screenModelScope.launchIO {
            jsPluginManager.repositories.collectLatest { repos ->
                mutableState.update {
                    NovelRepoScreenState.Success(
                        repos = repos.toImmutableList(),
                    )
                }
            }
        }
    }

    fun createRepo(name: String, url: String) {
        screenModelScope.launchIO {
            jsPluginManager.addRepository(name, url)
            dismissDialog()
        }
    }

    fun deleteRepo(url: String) {
        screenModelScope.launchIO {
            jsPluginManager.removeRepository(url)
            dismissDialog()
        }
    }

    fun refreshRepos() {
        screenModelScope.launchIO {
            jsPluginManager.refreshAvailablePlugins(forceRefresh = true)
        }
    }

    fun showDialog(dialog: NovelRepoDialog) {
        mutableState.update {
            when (it) {
                NovelRepoScreenState.Loading -> it
                is NovelRepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                NovelRepoScreenState.Loading -> it
                is NovelRepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class NovelRepoDialog {
    data object Create : NovelRepoDialog()
    data class Delete(val repo: JsPluginRepository) : NovelRepoDialog()
}

sealed class NovelRepoScreenState {
    @Immutable
    data object Loading : NovelRepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableList<JsPluginRepository>,
        val dialog: NovelRepoDialog? = null,
    ) : NovelRepoScreenState() {
        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
