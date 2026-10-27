package mihon.domain.extension.repository

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.model.ExtensionStore

interface ExtensionStoreRepository {
    suspend fun insert(indexUrl: String): Result<Unit>

    suspend fun insertFromPreference(indexUrl: String, name: String)

    suspend fun upsert(store: ExtensionStore)

    suspend fun refreshAll()

    /**
     * Lists the extensions offered by the stores whose index url is in [enabledStoreUrls]. Stores the
     * user hasn't turned on aren't contacted at all.
     */
    suspend fun fetchExtensions(enabledStoreUrls: Set<String>): List<Extension.Available>

    suspend fun getAll(): List<ExtensionStore>

    fun getAllAsFlow(): Flow<List<ExtensionStore>>

    fun getCountAsFlow(): Flow<Long>

    suspend fun remove(indexUrl: String)
}
