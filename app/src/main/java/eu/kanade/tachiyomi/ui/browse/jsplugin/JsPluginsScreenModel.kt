package eu.kanade.tachiyomi.ui.browse.jsplugin

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.jsplugin.model.InstalledJsPlugin
import eu.kanade.tachiyomi.jsplugin.model.JsPlugin
import eu.kanade.tachiyomi.jsplugin.model.JsPluginRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class JsPluginsScreenModel(
    private val jsPluginManager: JsPluginManager = Injekt.get(),
) : StateScreenModel<JsPluginsScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                jsPluginManager.repositories,
                jsPluginManager.availablePlugins,
                jsPluginManager.installedPlugins,
                jsPluginManager.isLoading,
            ) { repos, available, installed, loading ->
                State(
                    repositories = repos,
                    availablePlugins = available,
                    installedPlugins = installed,
                    isLoading = loading,
                    searchQuery = state.value.searchQuery,
                )
            }.collect { newState ->
                mutableState.value = newState
            }
        }
        
        // Initial refresh
        screenModelScope.launch {
            jsPluginManager.refreshAvailablePlugins()
        }
    }

    private val _searchQuery = MutableStateFlow<String?>(null)

    fun search(query: String?) {
        _searchQuery.value = query
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun refreshPlugins() {
        screenModelScope.launch {
            jsPluginManager.refreshAvailablePlugins()
        }
    }

    fun installPlugin(plugin: JsPlugin) {
        val repo = state.value.repositories.find { it.enabled }?.url ?: return
        screenModelScope.launch {
            jsPluginManager.installPlugin(plugin, repo)
        }
    }

    fun uninstallPlugin(plugin: InstalledJsPlugin) {
        screenModelScope.launch {
            jsPluginManager.uninstallPlugin(plugin.plugin.id)
        }
    }

    fun updatePlugin(plugin: JsPlugin) {
        val installed = state.value.installedPlugins.find { it.plugin.id == plugin.id } ?: return
        screenModelScope.launch {
            jsPluginManager.updatePlugin(installed)
        }
    }

    fun updateAllPlugins() {
        screenModelScope.launch {
            val updates = state.value.installedPlugins.filter { installed ->
                val available = state.value.availablePlugins.find { it.id == installed.plugin.id }
                available != null && available.version != installed.plugin.version
            }
            updates.forEach { installed ->
                jsPluginManager.updatePlugin(installed)
            }
        }
    }

    fun addRepository(name: String, url: String) {
        screenModelScope.launch {
            jsPluginManager.addRepository(name, url)
            jsPluginManager.refreshAvailablePlugins()
        }
    }

    fun removeRepository(repo: JsPluginRepository) {
        screenModelScope.launch {
            jsPluginManager.removeRepository(repo.url)
        }
    }

    fun toggleRepository(repo: JsPluginRepository) {
        screenModelScope.launch {
            jsPluginManager.setRepositoryEnabled(repo.url, !repo.enabled)
            jsPluginManager.refreshAvailablePlugins()
        }
    }

    /**
     * Get the display list of plugins (filtered by search if needed)
     */
    fun getFilteredPlugins(): List<JsPluginItem> {
        val query = state.value.searchQuery?.lowercase()
        val installed = state.value.installedPlugins.associateBy { it.plugin.id }
        
        return state.value.availablePlugins
            .filter { plugin ->
                query == null || 
                plugin.name.lowercase().contains(query) ||
                plugin.site.lowercase().contains(query) ||
                plugin.lang.lowercase().contains(query)
            }
            .map { plugin ->
                JsPluginItem(
                    plugin = plugin,
                    installed = installed[plugin.id],
                    hasUpdate = installed[plugin.id]?.let { it.plugin.version != plugin.version } ?: false,
                )
            }
            .sortedWith(compareBy(
                { it.installed == null }, // Installed first
                { !it.hasUpdate }, // Updates first
                { it.plugin.lang },
                { it.plugin.name }
            ))
    }

    /**
     * Get count of available updates
     */
    fun getUpdateCount(): Int {
        val installed = state.value.installedPlugins.associateBy { it.plugin.id }
        return state.value.availablePlugins.count { available ->
            val ins = installed[available.id]
            ins != null && ins.plugin.version != available.version
        }
    }

    data class State(
        val repositories: List<JsPluginRepository> = emptyList(),
        val availablePlugins: List<JsPlugin> = emptyList(),
        val installedPlugins: List<InstalledJsPlugin> = emptyList(),
        val isLoading: Boolean = false,
        val searchQuery: String? = null,
    )

    data class JsPluginItem(
        val plugin: JsPlugin,
        val installed: InstalledJsPlugin?,
        val hasUpdate: Boolean,
    )
}
