package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ExtensionStore
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ExtensionStoresViewModel(
    private val getExtensionStores: GetExtensionStores,
    private val addExtensionStore: AddExtensionStore,
    private val removeExtensionStore: RemoveExtensionStore,
    private val updateExtensionStores: UpdateExtensionStores,
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    private val dialog = MutableStateFlow<ExtensionStoreDialog?>(null)

    val state: StateFlow<ExtensionStoreScreenState> = combine(
        getExtensionStores.subscribe(),
        preferences.enabledExtensionStores.changes(),
        dialog,
    ) { stores, enabledStoreUrls, dialog ->
        ExtensionStoreScreenState.Success(
            stores = stores,
            enabledStoreUrls = enabledStoreUrls,
            dialog = dialog,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), ExtensionStoreScreenState.Loading)

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        viewModelScope.launch {
            dialog.update {
                when (it) {
                    is ExtensionStoreDialog.Create -> it.copy(processing = true)
                    is ExtensionStoreDialog.Confirm -> it.copy(processing = true)
                    else -> it
                }
            }
            addExtensionStore(baseUrl)
                .onSuccess {
                    extensionManager.findAvailableExtensions()
                    dismissDialog()
                }
                .onFailure { throwable ->
                    dialog.update {
                        when (it) {
                            is ExtensionStoreDialog.Create -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )
                            is ExtensionStoreDialog.Confirm -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )
                            else -> it
                        }
                    }
                }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        viewModelScope.launch(Dispatchers.IO) {
            updateExtensionStores()
        }
    }

    /**
     * Switches a repo on or off. Turning one on for the first time asks for the trust warning first:
     * an enabled store is one whose extensions the user lets run inside the app.
     */
    fun setRepoEnabled(store: ExtensionStore, enabled: Boolean) {
        if (!enabled) {
            extensionManager.setExtensionStoreEnabled(store.indexUrl, enabled = false)
            refreshAvailableExtensions()
            return
        }

        if (extensionManager.isExtensionStoreTrusted(store.indexUrl)) {
            extensionManager.setExtensionStoreEnabled(store.indexUrl, enabled = true)
            refreshAvailableExtensions()
        } else {
            showDialog(ExtensionStoreDialog.Trust(store))
        }
    }

    /**
     * Accepts the trust warning and turns the repo on.
     */
    fun confirmTrust(store: ExtensionStore) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.trustExtensionStore(store.indexUrl)
            extensionManager.findAvailableExtensions()
            dismissDialog()
        }
    }

    /**
     * Opens the removal warning for the given repo, listing what it will take with it.
     */
    fun requestDeleteRepo(store: ExtensionStore) {
        viewModelScope.launch(Dispatchers.IO) {
            val installedExtensions = extensionManager.getInstalledExtensionsOfStore(store.indexUrl)
            dialog.update {
                ExtensionStoreDialog.Delete(store = store, installedExtensions = installedExtensions)
            }
        }
    }

    /**
     * Deletes the given repo from the database along with every extension installed from it.
     */
    fun deleteRepo(baseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Resolve the extensions before the store is gone, so the ones it listed can still be traced back
            val installedExtensions = extensionManager.getInstalledExtensionsOfStore(baseUrl)
            removeExtensionStore(baseUrl)
            extensionManager.forgetExtensionStore(baseUrl)
            installedExtensions.forEach { extensionManager.uninstallExtension(it) }
            extensionManager.findAvailableExtensions()
        }
    }

    private fun refreshAvailableExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.findAvailableExtensions()
        }
    }

    fun addFromDeeplink(storeIndexUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val alreadyExists = getExtensionStores.get().any { it.indexUrl == storeIndexUrl }
            dialog.update { ExtensionStoreDialog.Confirm(url = storeIndexUrl, alreadyExists = alreadyExists) }
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed class ExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : ExtensionStoreDialog()
    data class Delete(
        val store: ExtensionStore,
        /** Everything installed from this store, so the warning can name what it removes. */
        val installedExtensions: List<Extension.Installed> = emptyList(),
    ) : ExtensionStoreDialog()
    data class Trust(val store: ExtensionStore) : ExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : ExtensionStoreDialog()
}

sealed class ExtensionStoreScreenState {

    @Immutable
    data object Loading : ExtensionStoreScreenState()

    @Immutable
    data class Success(
        val stores: List<ExtensionStore>,
        val enabledStoreUrls: Set<String> = emptySet(),
        val dialog: ExtensionStoreDialog? = null,
    ) : ExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
