package mihon.domain.extension.interactor

import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.Json
import mihon.domain.extension.repository.ExtensionStoreRepository

@Inject
class GetExtensionStoreCountAsFlow(
    private val repository: ExtensionStoreRepository,
) {
    operator fun invoke() = repository.getCountAsFlow()
}
