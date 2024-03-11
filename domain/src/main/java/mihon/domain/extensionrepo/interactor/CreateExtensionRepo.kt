package mihon.domain.extensionrepo.interactor

import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository

class CreateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo): Result {
        extensionRepoRepository.upsertRepository(
            repo.baseUrl,
            repo.name,
            repo.shortName,
            repo.website,
            repo.fingerprint,
        )

        return Result.Success
    }

    sealed interface Result {
        data object DuplicateFingerprint : Result
        data object Success : Result
    }
}
