package mihon.domain.extension.interactor

import mihon.domain.extension.repository.ExtensionStoreRepository

class RemoveExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
