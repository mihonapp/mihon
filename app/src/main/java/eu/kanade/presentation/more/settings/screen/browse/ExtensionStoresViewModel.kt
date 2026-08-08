package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.extension.ExtensionManager
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
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class ExtensionStoresViewModel(
    private val getExtensionStores: GetExtensionStores = Injekt.get(),
    private val addExtensionStore: AddExtensionStore = Injekt.get(),
    private val removeExtensionStore: RemoveExtensionStore = Injekt.get(),
    private val updateExtensionStores: UpdateExtensionStores = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
) : ViewModel() {

    private val dialog = MutableStateFlow<ExtensionStoreDialog?>(null)

    val state: StateFlow<ExtensionStoreScreenState> = combine(
        getExtensionStores.subscribe(),
        dialog,
    ) { stores, dialog ->
        ExtensionStoreScreenState.Success(stores = stores, dialog = dialog)
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
        viewModelScope.launchIO {
            updateExtensionStores()
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        viewModelScope.launchIO {
            removeExtensionStore(baseUrl)
            extensionManager.findAvailableExtensions()
        }
    }

    fun addFromDeeplink(storeIndexUrl: String) {
        viewModelScope.launchIO {
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
    data class Delete(val store: ExtensionStore) : ExtensionStoreDialog()
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
        val dialog: ExtensionStoreDialog? = null,
    ) : ExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
