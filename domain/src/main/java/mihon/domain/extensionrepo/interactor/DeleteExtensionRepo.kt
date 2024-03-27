package mihon.domain.extensionrepo.interactor

import mihon.domain.extensionrepo.repository.ExtensionRepoRepository

class DeleteExtensionRepo(
    private val repository: ExtensionRepoRepository,
) {
    suspend fun await(baseUrl: String) {
        repository.deleteRepo(baseUrl)
    }
}
