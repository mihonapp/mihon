package eu.kanade.tachiyomi.ui.browse.extension

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class NovelExtensionsScreenModel(
    preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val getExtensions: GetExtensionsByType = Injekt.get(),
) : StateScreenModel<ExtensionsScreenModel.State>(ExtensionsScreenModel.State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    init {
        val context = Injekt.get<Application>()
        val extensionMapper: (Map<String, InstallStep>) -> ((Extension) -> ExtensionUiModel.Item) = { map ->
            {
                ExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
            }
        }
        val queryFilter: (String) -> ((Extension) -> Boolean) = { query ->
            filter@{ extension ->
                if (query.isEmpty()) return@filter true
                query.split(",").any { _input ->
                    val input = _input.trim()
                    if (input.isEmpty()) return@any false
                    when (extension) {
                        is Extension.Available -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.baseUrl.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull()
                            } ||
                                extension.name.contains(input, ignoreCase = true)
                        }
                        is Extension.Installed -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull() ||
                                    if (it is HttpSource) {
                                        it.baseUrl.contains(input, ignoreCase = true)
                                    } else {
                                        false
                                    }
                            } ||
                                extension.name.contains(input, ignoreCase = true)
                        }
                        is Extension.Untrusted -> extension.name.contains(input, ignoreCase = true)
                        is Extension.JsPlugin -> 
                            extension.name.contains(input, ignoreCase = true) ||
                                extension.lang.contains(input, ignoreCase = true) ||
                                extension.pkgName.contains(input, ignoreCase = true)
                    }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                currentDownloads,
                getExtensions.subscribe(),
                jsPluginManager.availablePlugins,
                jsPluginManager.installedPlugins,
            ) { query, downloads, (_updates, _installed, _available, _untrusted), jsAvailable, jsInstalled ->
                val searchQuery = query ?: ""

                val itemsGroups: ItemGroups = mutableMapOf()

                // Build extensions from available plugins
                val jsExtensions = jsAvailable.map { plugin ->
                    val installed = jsInstalled.find { it.plugin.id == plugin.id }
                    val verCode = plugin.version.replace(".", "").toLongOrNull() ?: 0L
                    val instVerCode = installed?.plugin?.version?.replace(".", "")?.toLongOrNull() ?: 0L
                    Extension.JsPlugin(
                        name = plugin.name,
                        pkgName = plugin.id,
                        versionName = plugin.version,
                        versionCode = verCode,
                        libVersion = 0.0,
                        lang = plugin.lang,
                        isNsfw = false,
                        isNovel = true,
                        sources = listOf(
                            Extension.Available.Source(
                                id = plugin.sourceId(),
                                lang = plugin.lang,
                                name = plugin.name,
                                baseUrl = plugin.site
                            )
                        ),
                        iconUrl = plugin.iconUrl,
                        repoUrl = plugin.repositoryUrl ?: "",
                        isInstalled = installed != null,
                        hasUpdate = installed != null && verCode > instVerCode
                    )
                }

                // Add installed plugins that aren't in available list (e.g., repo removed)
                val availableIds = jsAvailable.map { it.id }.toSet()
                val installedOnlyExtensions = jsInstalled
                    .filter { it.plugin.id !in availableIds }
                    .map { installed ->
                        val plugin = installed.plugin
                        val verCode = plugin.version.replace(".", "").toLongOrNull() ?: 0L
                        Extension.JsPlugin(
                            name = plugin.name,
                            pkgName = plugin.id,
                            versionName = plugin.version,
                            versionCode = verCode,
                            libVersion = 0.0,
                            lang = plugin.lang,
                            isNsfw = false,
                            isNovel = true,
                            sources = listOf(
                                Extension.Available.Source(
                                    id = plugin.sourceId(),
                                    lang = plugin.lang,
                                    name = plugin.name,
                                    baseUrl = plugin.site
                                )
                            ),
                            iconUrl = plugin.iconUrl,
                            repoUrl = installed.repositoryUrl,
                            isInstalled = true,
                            hasUpdate = false
                        )
                    }

                val allJsExtensions = jsExtensions + installedOnlyExtensions
                val jsUpdates = allJsExtensions.filter { it.hasUpdate }
                val jsInstalledExt = allJsExtensions.filter { it.isInstalled && !it.hasUpdate }
                val jsAvailableExt = allJsExtensions.filter { !it.isInstalled }

                val updates = (_updates.filter { it.isNovel } + jsUpdates)
                    .filter(queryFilter(searchQuery)).map(extensionMapper(downloads))
                if (updates.isNotEmpty()) {
                    itemsGroups[ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending)] = updates
                }

                val installed = (_installed.filter {
                    it.isNovel
                } + jsInstalledExt).filter(queryFilter(searchQuery)).map(extensionMapper(downloads))
                val untrusted = _untrusted.filter {
                    it.isNovel
                }.filter(queryFilter(searchQuery)).map(extensionMapper(downloads))
                if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                    itemsGroups[ExtensionUiModel.Header.Resource(MR.strings.ext_installed)] = (installed + untrusted)
                }

                val languagesWithExtensions = (_available
                    .filter { it.isNovel } + jsAvailableExt)
                    .filter(queryFilter(searchQuery))
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)
                    .map { (lang, extensions) ->
                        ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                            extensions.map(extensionMapper(downloads))
                    }

                if (languagesWithExtensions.isNotEmpty()) {
                    itemsGroups.putAll(languagesWithExtensions)
                }

                itemsGroups
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = it,
                        )
                    }
                }
        }

        screenModelScope.launchIO { findAvailableExtensions() }

        preferences.extensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<Extension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: Extension.Available) {
        screenModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun installJsPlugin(extension: Extension.JsPlugin) {
        screenModelScope.launchIO {
            val plugin = jsPluginManager.availablePlugins.value.find { it.id == extension.pkgName }
            if (plugin == null) {
                logcat(LogPriority.ERROR) { "Plugin not found in available plugins: ${extension.pkgName}" }
                return@launchIO
            }
            if (extension.repoUrl.isEmpty()) {
                logcat(LogPriority.ERROR) { "Plugin repo URL is empty: ${extension.pkgName}" }
                return@launchIO
            }
            
            logcat(LogPriority.INFO) { "Installing JS plugin: ${extension.name} from ${extension.repoUrl}" }
            jsPluginManager.installPlugin(plugin, extension.repoUrl)
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
        removeDownloadState(extension)
    }

    fun uninstallExtension(extension: Extension) {
        when (extension) {
            is Extension.JsPlugin -> {
                screenModelScope.launchIO {
                    jsPluginManager.uninstallPlugin(extension.pkgName)
                }
            }
            else -> extensionManager.uninstallExtension(extension)
        }
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: Extension) {
        currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()
            jsPluginManager.refreshAvailablePlugins(forceRefresh = true)

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustExtension(extension: Extension.Untrusted) {
        screenModelScope.launch {
            extensionManager.trust(extension)
        }
    }
}
