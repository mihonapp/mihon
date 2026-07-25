package mihon.domain.extension.interactor

import mihon.domain.extension.repository.ExtensionStoreRepository

class GetExtensionStoreCountAsFlow(
    private val repository: ExtensionStoreRepository,
) {
    operator fun invoke() = repository.getCountAsFlow()
}
