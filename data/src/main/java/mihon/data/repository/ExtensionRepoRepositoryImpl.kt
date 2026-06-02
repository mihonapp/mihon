package mihon.data.repository

import android.database.SQLException
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne

class ExtensionRepoRepositoryImpl(
    private val database: Database,
) : ExtensionRepoRepository {
    override fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return database.extension_reposQueries
            .findAll(::mapExtensionRepo)
            .subscribeToList()
    }

    override suspend fun getAll(): List<ExtensionRepo> {
        return database.extension_reposQueries
            .findAll(::mapExtensionRepo)
            .awaitAsList()
    }

    override suspend fun getRepo(baseUrl: String): ExtensionRepo? {
        return database.extension_reposQueries
            .findOne(baseUrl, ::mapExtensionRepo)
            .awaitAsOneOrNull()
    }

    override suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? {
        return database.extension_reposQueries
            .findOneBySigningKeyFingerprint(fingerprint, ::mapExtensionRepo)
            .awaitAsOneOrNull()
    }

    override fun getCount(): Flow<Int> {
        return database.extension_reposQueries
            .count()
            .subscribeToOne()
            .map { it.toInt() }
    }

    override suspend fun insertRepo(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        signingKeyFingerprint: String,
    ) {
        try {
            database.extension_reposQueries.insert(
                baseUrl,
                name,
                shortName,
                website,
                signingKeyFingerprint,
            )
        } catch (ex: SQLException) {
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
            database.extension_reposQueries.upsert(
                baseUrl,
                name,
                shortName,
                website,
                signingKeyFingerprint,
            )
        } catch (ex: SQLException) {
            throw SaveExtensionRepoException(ex)
        }
    }

    override suspend fun replaceRepo(newRepo: ExtensionRepo) {
        database.extension_reposQueries.replace(
            newRepo.baseUrl,
            newRepo.name,
            newRepo.shortName,
            newRepo.website,
            newRepo.signingKeyFingerprint,
        )
    }

    override suspend fun deleteRepo(baseUrl: String) {
        database.extension_reposQueries.delete(baseUrl)
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
