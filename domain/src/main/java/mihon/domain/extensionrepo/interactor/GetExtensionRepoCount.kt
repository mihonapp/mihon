package mihon.domain.extensionrepo.interactor

import mihon.domain.extensionrepo.repository.ExtensionRepoRepository

class GetExtensionRepoCount(
    private val extensionRepoRepository: ExtensionRepoRepository,
) {
    suspend fun subscribe() = extensionRepoRepository.getCount()
}
