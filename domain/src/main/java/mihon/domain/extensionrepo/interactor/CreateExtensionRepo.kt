package mihon.domain.extensionrepo.interactor

import android.database.sqlite.SQLiteConstraintException
import eu.kanade.tachiyomi.network.NetworkHelper
import logcat.LogPriority
import mihon.domain.extensionrepo.api.ExtensionRepoApi
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

class CreateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository,
) {
    private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()

    private val networkService: NetworkHelper by injectLazy()

    private val client: OkHttpClient
        get() = networkService.client

    private val extensionRepoApi = ExtensionRepoApi(client)

    suspend fun await(repoUrl: String): Result {
        if (!repoUrl.matches(repoRegex)) {
            return Result.InvalidUrl
        }

        val baseUrl = repoUrl.removeSuffix("/index.min.json")
        return extensionRepoApi.fetchRepoDetails(baseUrl)?.let { insert(it) } ?: Result.InvalidUrl
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        val result = try {
            extensionRepoRepository.insertRepository(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.fingerprint,
            )
            Result.Success
        } catch (ex: SQLiteConstraintException) {
            logcat(LogPriority.WARN, ex) { "SQL Conflict attempting to add new repository ${repo.baseUrl}" }
            // SQLDelight doesn't provide constraint info in exceptions.
            // First check if the conflict was on primary key. if so return RepoAlreadyExists
            // Then check if the conflict was on fingerprint. if so Return DuplicateFingerprint
            extensionRepoRepository.getRepository(baseUrl = repo.baseUrl)
                ?.let { Result.RepoAlreadyExists }
                ?: extensionRepoRepository.getRepositoryByFingerprint(fingerprint = repo.fingerprint)
                    ?.let { Result.DuplicateFingerprint(it, repo) }
                ?: Result.Error
        }
        return result
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
