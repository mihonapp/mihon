package mihon.domain.extension.interactor

import mihon.domain.extension.repository.ExtensionStoreRepository

class UpdateExtensionStores(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke() {
        repository.refreshAll()
    }
}
