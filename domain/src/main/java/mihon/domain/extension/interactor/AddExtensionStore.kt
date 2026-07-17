package mihon.domain.extension.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.repository.ExtensionStoreRepository

@Inject
class AddExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String): Result<Unit> {
        return repository.insert(indexUrl)
    }
}
