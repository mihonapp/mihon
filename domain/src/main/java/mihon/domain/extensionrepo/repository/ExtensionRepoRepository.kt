package mihon.domain.extensionrepo.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.model.ExtensionRepo

interface ExtensionRepoRepository {

    fun subscribeAll(): Flow<List<ExtensionRepo>>

    suspend fun getRepository(baseUrl: String): ExtensionRepo?

    suspend fun upsertRepository(
        baseUrl: String,
        name: String,
        shortName: String?,
        website: String,
        fingerprint: String,
    )

}
