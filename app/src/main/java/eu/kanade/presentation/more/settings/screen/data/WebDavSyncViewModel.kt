package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.sync.webdav.SyncInstalledExtension
import eu.kanade.tachiyomi.data.sync.webdav.WebDavConfig
import eu.kanade.tachiyomi.data.sync.webdav.WebDavSyncManager
import eu.kanade.tachiyomi.data.sync.webdav.WebDavSyncWorker
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.WebDavSyncPreferences
import tachiyomi.i18n.MR

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class WebDavSyncViewModel(
    private val context: Context,
    val preferences: WebDavSyncPreferences,
    private val syncManager: WebDavSyncManager,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val installSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())

    init {
        viewModelScope.launch {
            syncManager.isSyncing.collect { syncing ->
                _state.update { it.copy(isSyncing = syncing) }
            }
        }

        // Refresh persisted missing list whenever installed extensions change
        viewModelScope.launch {
            combine(
                extensionManager.loadedExtensionsFlow,
                extensionManager.notLoadedExtensionsFlow,
            ) { loaded, notLoaded ->
                (loaded.map { it.pkgName } + notLoaded.map { it.pkgName }).toSet()
            }.collectLatest {
                syncManager.refreshMissingExtensions()
            }
        }

        viewModelScope.launch {
            combine(
                preferences.missingExtensionsJson.changes(),
                extensionManager.availableExtensionsFlow,
                installSteps,
            ) { _, availableExtensions, steps ->
                val missing = syncManager.getMissingExtensions()
                val availableByPkg = availableExtensions.groupBy { it.pkgName }
                missing.map { ext ->
                    val candidates = availableByPkg[ext.pkgName].orEmpty()
                    val matched = candidates.firstOrNull {
                        ext.signingKey != null && it.store.signingKey == ext.signingKey
                    } ?: candidates.firstOrNull {
                        ext.storeIndexUrl != null && it.store.indexUrl == ext.storeIndexUrl
                    } ?: candidates.firstOrNull()

                    MissingExtensionItem(
                        synced = ext,
                        available = matched,
                        installStep = steps[ext.pkgName] ?: InstallStep.Idle,
                    )
                }
            }.collectLatest { items ->
                _state.update { it.copy(missingExtensions = items) }
            }
        }
    }

    fun updateServerConfig(
        serverUrl: String,
        username: String,
        password: String,
        remotePath: String,
    ) {
        val previousUrl = preferences.serverUrl.get().trim()
        val previousPath = preferences.remotePath.get().trim()
        val newUrl = serverUrl.trim()
        val newPath = remotePath.trim().ifBlank { "/mihon" }

        preferences.serverUrl.set(newUrl)
        preferences.username.set(username)
        preferences.password.set(password)
        preferences.remotePath.set(newPath)

        // Switching to a different server or directory invalidates the previous 3-way baseline
        if (previousUrl != newUrl || previousPath != newPath) {
            syncManager.clearLocalBase()
        }

        WebDavSyncWorker.setupPeriodicTask(context)
    }

    fun onScheduleSettingChanged() {
        WebDavSyncWorker.setupPeriodicTask(context)
    }

    fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
        remotePath: String,
    ) {
        val trimmedUrl = serverUrl.trim()
        if (trimmedUrl.isBlank()) {
            viewModelScope.launch {
                _events.send(Event.ShowMessage(context.stringResource(MR.strings.webdav_sync_not_configured)))
            }
            return
        }

        updateServerConfig(serverUrl, username, password, remotePath)

        viewModelScope.launch {
            _state.update { it.copy(isTestingConnection = true) }
            try {
                syncManager.testConnection(
                    WebDavConfig(
                        serverUrl = trimmedUrl,
                        username = username,
                        password = password,
                        remotePath = remotePath.trim().ifBlank { "/mihon" },
                    ),
                )
                _events.send(Event.ShowMessage(context.stringResource(MR.strings.webdav_sync_connection_success)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "WebDAV connection test failed" }
                val message = e.message ?: e::class.simpleName.orEmpty()
                _events.send(
                    Event.ShowMessage(context.stringResource(MR.strings.webdav_sync_connection_failed, message)),
                )
            } finally {
                _state.update { it.copy(isTestingConnection = false) }
            }
        }
    }

    fun syncNow(
        serverUrl: String,
        username: String,
        password: String,
        remotePath: String,
    ) {
        if (serverUrl.trim().isBlank()) {
            viewModelScope.launch {
                _events.send(Event.ShowMessage(context.stringResource(MR.strings.webdav_sync_not_configured)))
            }
            return
        }

        updateServerConfig(serverUrl, username, password, remotePath)
        WebDavSyncWorker.startNow(context)
    }

    fun openMissingExtensionsDialog() {
        _state.update { it.copy(showMissingExtensionsDialog = true) }
        if (extensionManager.availableExtensionsFlow.value.isEmpty()) {
            refreshExtensionStores()
        }
    }

    fun dismissMissingExtensionsDialog() {
        _state.update { it.copy(showMissingExtensionsDialog = false) }
    }

    fun refreshExtensionStores() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshingStores = true) }
            try {
                extensionManager.findAvailableExtensions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to refresh extension stores" }
            } finally {
                _state.update { it.copy(isRefreshingStores = false) }
            }
        }
    }

    fun installExtension(item: MissingExtensionItem) {
        val available = item.available ?: return
        viewModelScope.launch {
            installSingleExtension(available)
        }
    }

    fun installAllAvailable() {
        val targets = state.value.missingExtensions.mapNotNull { item ->
            item.available?.takeIf { item.installStep == InstallStep.Idle || item.installStep == InstallStep.Error }
        }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            for (extension in targets) {
                installSingleExtension(extension)
            }
        }
    }

    private suspend fun installSingleExtension(extension: Extension.Available) {
        installSteps.update { it + (extension.pkgName to InstallStep.Pending) }
        try {
            extensionManager.installExtension(extension).collect { step ->
                installSteps.update { it + (extension.pkgName to step) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install extension ${extension.pkgName}" }
            installSteps.update { it + (extension.pkgName to InstallStep.Error) }
        }
    }

    @Immutable
    data class MissingExtensionItem(
        val synced: SyncInstalledExtension,
        val available: Extension.Available?,
        val installStep: InstallStep = InstallStep.Idle,
    )

    @Immutable
    data class State(
        val isSyncing: Boolean = false,
        val isTestingConnection: Boolean = false,
        val isRefreshingStores: Boolean = false,
        val missingExtensions: List<MissingExtensionItem> = emptyList(),
        val showMissingExtensionsDialog: Boolean = false,
    )

    sealed interface Event {
        data class ShowMessage(val message: String) : Event
    }
}
