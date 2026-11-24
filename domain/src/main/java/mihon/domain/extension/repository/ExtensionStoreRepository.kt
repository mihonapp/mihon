package mihon.domain.extension.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.ExtensionStoreListings

interface ExtensionStoreRepository {
    suspend fun insert(indexUrl: String): Result<Unit>

    suspend fun insertFromPreference(indexUrl: String, name: String)

    suspend fun upsert(store: ExtensionStore)

    suspend fun refreshAll()

    suspend fun fetchExtensions(): ExtensionStoreListings

    suspend fun getAll(): List<ExtensionStore>

    fun getAllAsFlow(): Flow<List<ExtensionStore>>

    fun getCountAsFlow(): Flow<Long>

    suspend fun remove(indexUrl: String)
}
