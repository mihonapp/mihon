package mihon.domain.extensionrepo.interactor

import eu.kanade.tachiyomi.network.NetworkHelper
import mihon.domain.extensionrepo.api.ExtensionRepoApi
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository

class UpdateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository,
    networkService: NetworkHelper,
) {

    private val extensionRepoApi = ExtensionRepoApi(networkService.client)

    suspend fun await(repo: ExtensionRepo): ExtensionRepo? {
        val newRepo = extensionRepoApi.fetchRepoDetails(repo.baseUrl)
        newRepo?.let {
            if (repo.fingerprint.startsWith("NOFINGERPRINT") || repo.fingerprint == newRepo.fingerprint) {
                extensionRepoRepository.upsertRepository(newRepo)
            }
            return newRepo
        }
        return null
    }

}
