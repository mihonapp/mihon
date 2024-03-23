package mihon.domain.extensionrepo.interactor

import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository

class ReplaceExtensionRepo(
    private val repository: ExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        repository.replaceRepo(repo)
    }
}
