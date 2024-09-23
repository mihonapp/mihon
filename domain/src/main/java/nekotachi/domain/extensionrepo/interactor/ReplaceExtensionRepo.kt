package nekotachi.domain.extensionrepo.interactor

import nekotachi.domain.extensionrepo.model.ExtensionRepo
import nekotachi.domain.extensionrepo.repository.ExtensionRepoRepository

class ReplaceExtensionRepo(
    private val repository: ExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        repository.replaceRepo(repo)
    }
}
