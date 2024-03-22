package mihon.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.data.DatabaseHandler

class ExtensionRepoRepositoryImpl(
    private val handler: DatabaseHandler,
) : ExtensionRepoRepository {
    override fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return handler.subscribeToList { extension_reposQueries.findAll(::mapExtensionRepo) }
    }

    override suspend fun getAll(): List<ExtensionRepo> {
        return handler.awaitList { extension_reposQueries.findAll(::mapExtensionRepo) }
    }

    override suspend fun getRepository(baseUrl: String): ExtensionRepo? {
        return handler.awaitOneOrNull { extension_reposQueries.findOne(baseUrl, ::mapExtensionRepo) }
    }

    override suspend fun getRepositoryByFingerprint(fingerprint: String): ExtensionRepo? {
        return handler.awaitOneOrNull { extension_reposQueries.findOneByFingerprint(fingerprint, ::mapExtensionRepo) }
    }

    override suspend fun getCount(): Int {
        return handler.awaitOne { extension_reposQueries.count() }.toInt()
    }

    override suspend fun insertRepository(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        fingerprint: String,
    ) {
        try {
            handler.await { extension_reposQueries.insert(baseUrl, name, shortName, website, fingerprint) }
        } catch (ex: SQLiteException) {
            throw SaveExtensionRepoException(ex)
        }
    }

    override suspend fun upsertRepository(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        fingerprint: String,
    ) {
        try {
            handler.await { extension_reposQueries.upsert(baseUrl, name, shortName, website, fingerprint) }
        } catch (ex: SQLiteException) {
            throw SaveExtensionRepoException(ex)
        }
    }

    override suspend fun replaceRepository(newRepo: ExtensionRepo) {
        handler.await {
            extension_reposQueries.replace(
                newRepo.baseUrl,
                newRepo.name,
                newRepo.shortName,
                newRepo.website,
                newRepo.fingerprint,
            )
        }
    }

    override suspend fun deleteRepository(baseUrl: String) {
        return handler.await { extension_reposQueries.delete(baseUrl) }
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
