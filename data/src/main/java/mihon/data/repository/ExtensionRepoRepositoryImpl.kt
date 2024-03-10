package mihon.data.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.data.DatabaseHandler

class ExtensionRepoRepositoryImpl(
    private val handler: DatabaseHandler,
) : ExtensionRepoRepository {
    override fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return handler.subscribeToList { extension_reposQueries.findAll(::mapExtensionRepo) }
    }

    override suspend fun getRepository(baseUrl: String): ExtensionRepo? {
        return handler.awaitOneOrNull { extension_reposQueries.findOne(baseUrl, ::mapExtensionRepo) }
    }

    override suspend fun upsertRepository(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        fingerprint: String,
    ) {
        handler.await { extension_reposQueries.upsert(baseUrl, name, shortName, website, fingerprint) }
    }


    private fun mapExtensionRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        fingerprint: String,
    ): ExtensionRepo = ExtensionRepo(
        baseUrl = baseUrl,
        name = name,
        shortName = shortName,
        website = website,
        fingerprint = fingerprint,
    )
}
