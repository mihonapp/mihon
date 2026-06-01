package mihon.domain.extensionrepo.interactor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.core.common.util.lang.withIOContext

class UpdateExtensionRepo(
    private val repository: ExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {

    suspend fun awaitAll() = withIOContext {
        coroutineScope {
            repository.getAll()
                .map { async { await(it) } }
                .awaitAll()
        }
    }

    suspend fun await(repo: ExtensionRepo) = withIOContext {
        val newRepo = service.fetchRepoDetails(repo.baseUrl) ?: return@withIOContext
        if (
            repo.signingKeyFingerprint.startsWith("NOFINGERPRINT") ||
            repo.signingKeyFingerprint == newRepo.signingKeyFingerprint
        ) {
            repository.upsertRepo(newRepo)
        }
    }
}
