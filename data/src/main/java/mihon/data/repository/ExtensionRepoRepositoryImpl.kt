package mihon.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    override suspend fun getRepo(baseUrl: String): ExtensionRepo? {
        return handler.awaitOneOrNull { extension_reposQueries.findOne(baseUrl, ::mapExtensionRepo) }
    }

    override suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? {
        return handler.awaitOneOrNull {
            extension_reposQueries.findOneBySigningKeyFingerprint(fingerprint, ::mapExtensionRepo)
        }
    }

    override fun getCount(): Flow<Int> {
        return handler.subscribeToOne { extension_reposQueries.count() }.map { it.toInt() }
    }

    override suspend fun insertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        try {
            handler.await { extension_reposQueries.insert(baseUrl, name, shortName, website, signingKeyFingerprint) }
        } catch (ex: SQLiteException) {
            throw SaveExtensionRepoException(ex)
        }
    }

    override suspend fun upsertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        try {
            handler.await { extension_reposQueries.upsert(baseUrl, name, shortName, website, signingKeyFingerprint) }
        } catch (ex: SQLiteException) {
            throw SaveExtensionRepoException(ex)
        }
    }

    override suspend fun replaceRepo(newRepo: ExtensionRepo) {
        handler.await {
            extension_reposQueries.replace(
                newRepo.baseUrl,
                newRepo.name,
                newRepo.shortName,
                newRepo.website,
                newRepo.signingKeyFingerprint,
            )
        }
    }

    override suspend fun deleteRepo(baseUrl: String) {
        return handler.await { extension_reposQueries.delete(baseUrl) }
    }

    private fun mapExtensionRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ): ExtensionRepo = ExtensionRepo(
        baseUrl = baseUrl,
        name = name,
        shortName = shortName,
        website = website,
        signingKeyFingerprint = signingKeyFingerprint,
    )
}
