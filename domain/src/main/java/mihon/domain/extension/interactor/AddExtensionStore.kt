package mihon.domain.extension.interactor

import mihon.domain.extension.repository.ExtensionStoreRepository

class AddExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String): Result<Unit> {
        return repository.insert(indexUrl)
    }
}
