package mihon.domain.extension.interactor

import kotlinx.serialization.json.Json
import mihon.domain.extension.repository.ExtensionStoreRepository

class GetExtensionStoreCountAsFlow(
    private val repository: ExtensionStoreRepository,
) {
    operator fun invoke() = repository.getCountAsFlow()
}
