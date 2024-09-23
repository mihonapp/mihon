package nekotachi.domain.extensionrepo.interactor

import nekotachi.domain.extensionrepo.repository.ExtensionRepoRepository

class DeleteExtensionRepo(
    private val repository: ExtensionRepoRepository,
) {
    suspend fun await(baseUrl: String) {
        repository.deleteRepo(baseUrl)
    }
}
