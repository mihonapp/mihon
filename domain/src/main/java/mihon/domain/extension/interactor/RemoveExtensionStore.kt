package mihon.domain.extension.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.repository.ExtensionStoreRepository

@Inject
class RemoveExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
