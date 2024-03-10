package mihon.domain.extensionrepo.interactor

import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository

class CreateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        extensionRepoRepository.upsertRepository(
            repo.baseUrl,
            repo.name,
            repo.shortName,
            repo.website,
            repo.fingerprint,
        )
    }
}
