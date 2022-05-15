package eu.kanade.tachiyomi.ui.browse.extension

import android.app.Application
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.extension.interactor.GetExtensionUpdates
import eu.kanade.domain.extension.interactor.GetExtensions
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionsPresenter(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getExtensionUpdates: GetExtensionUpdates = Injekt.get(),
    private val getExtensions: GetExtensions = Injekt.get(),
) : BasePresenter<ExtensionsController>() {

    private val _query: MutableStateFlow<String> = MutableStateFlow("")

    private var _currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    private val _state: MutableStateFlow<ExtensionState> = MutableStateFlow(ExtensionState.Uninitialized)
    val state: StateFlow<ExtensionState> = _state.asStateFlow()

    var isRefreshing: Boolean by mutableStateOf(true)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        extensionManager.findAvailableExtensions()

        val context = Injekt.get<Application>()
        val extensionMapper: (Map<String, InstallStep>) -> ((Extension) -> ExtensionUiModel) = { map ->
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
                            } || extension.name.contains(input, ignoreCase = true)
                        }
                        is Extension.Installed -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull() ||
                                    if (it is HttpSource) { it.baseUrl.contains(input, ignoreCase = true) } else false
                            } || extension.name.contains(input, ignoreCase = true)
                        }
                        is Extension.Untrusted -> extension.name.contains(input, ignoreCase = true)
                    }
                }
            }
        }

        launchIO {
            combine(
                _query,
                getExtensions.subscribe(),
                getExtensionUpdates.subscribe(),
                _currentDownloads,
            ) { query, (installed, untrusted, available), updates, downloads ->
                isRefreshing = false

                val languagesWithExtensions = available
                    .filter(queryFilter(query))
                    .groupBy { LocaleHelper.getSourceDisplayName(it.lang, context) }
                    .toSortedMap()
                    .flatMap { (key, value) ->
                        listOf(
                            ExtensionUiModel.Header.Text(key),
                            *value.map(extensionMapper(downloads)).toTypedArray(),
                        )
                    }

                val items = mutableListOf<ExtensionUiModel>()

                val updates = updates.filter(queryFilter(query)).map(extensionMapper(downloads))
                if (updates.isNotEmpty()) {
                    items.add(ExtensionUiModel.Header.Resource(R.string.ext_updates_pending))
                    items.addAll(updates)
                }

                val installed = installed.filter(queryFilter(query)).map(extensionMapper(downloads))
                val untrusted = untrusted.filter(queryFilter(query)).map(extensionMapper(downloads))
                if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                    items.add(ExtensionUiModel.Header.Resource(R.string.ext_installed))
                    items.addAll(installed)
                    items.addAll(untrusted)
                }

                if (languagesWithExtensions.isNotEmpty()) {
                    items.addAll(languagesWithExtensions)
                }

                items
            }.collectLatest {
                _state.value = ExtensionState.Initialized(it)
            }
        }
    }

    fun search(query: String) {
        launchIO {
            _query.emit(query)
        }
    }

    fun updateAllExtensions() {
        launchIO {
            val state = _state.value
            if (state !is ExtensionState.Initialized) return@launchIO
            state.list.mapNotNull {
                if (it !is ExtensionUiModel.Item) return@mapNotNull null
                if (it.extension !is Extension.Installed) return@mapNotNull null
                if (it.extension.hasUpdate.not()) return@mapNotNull null
                it.extension
            }.forEach {
                updateExtension(it)
            }
        }
    }

    fun installExtension(extension: Extension.Available) {
        extensionManager.installExtension(extension).subscribeToInstallUpdate(extension)
    }

    fun updateExtension(extension: Extension.Installed) {
        extensionManager.updateExtension(extension).subscribeToInstallUpdate(extension)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
    }

    private fun removeDownloadState(extension: Extension) {
        _currentDownloads.update { map ->
            val map = map.toMutableMap()
            map.remove(extension.pkgName)
            map
        }
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        _currentDownloads.update { map ->
            val map = map.toMutableMap()
            map[extension.pkgName] = installStep
            map
        }
    }

    private fun Observable<InstallStep>.subscribeToInstallUpdate(extension: Extension) {
        this
            .doOnUnsubscribe { removeDownloadState(extension) }
            .subscribe(
                { installStep -> addDownloadState(extension, installStep) },
                { removeDownloadState(extension) },
            )
    }

    fun uninstallExtension(pkgName: String) {
        extensionManager.uninstallExtension(pkgName)
    }

    fun findAvailableExtensions() {
        isRefreshing = true
        extensionManager.findAvailableExtensions()
    }

    fun trustSignature(signatureHash: String) {
        extensionManager.trustSignature(signatureHash)
    }
}

sealed interface ExtensionUiModel {
    sealed interface Header : ExtensionUiModel {
        data class Resource(@StringRes val textRes: Int) : Header
        data class Text(val text: String) : Header
    }
    data class Item(
        val extension: Extension,
        val installStep: InstallStep,
    ) : ExtensionUiModel {

        fun key(): String {
            return when (extension) {
                is Extension.Installed ->
                    if (extension.hasUpdate) "update_${extension.pkgName}" else extension.pkgName
                else -> extension.pkgName
            }
        }
    }
}

sealed class ExtensionState {
    object Uninitialized : ExtensionState()
    data class Initialized(val list: List<ExtensionUiModel>) : ExtensionState()
}
