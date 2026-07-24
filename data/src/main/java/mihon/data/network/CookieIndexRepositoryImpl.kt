package mihon.data.network

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import mihon.domain.network.CookieIndex
import mihon.domain.network.CookieIndexRepository
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne

class CookieIndexRepositoryImpl(
    private val database: Database,
) : CookieIndexRepository {
    override suspend fun insertHost(host: String) {
        database.cookie_indicesQueries.insertHost(host)
    }

    override suspend fun updateCookieIndex(host: String, cookieIndex: CookieIndex?, toRemove: List<CookieIndex>) {
        database.transaction {
            val currentList = database.cookie_indicesQueries.getCookieIndex(host).awaitAsOneOrNull() ?: emptyList()
            val updatedList = (currentList + listOfNotNull(cookieIndex))
                .distinct()
                .filterNot { it in toRemove }

            if (updatedList == currentList) return@transaction
            database.cookie_indicesQueries.insert(host, updatedList)
        }
    }

    override fun getHosts(): Flow<List<String>> {
        return database.cookie_indicesQueries.getHosts().subscribeToList()
    }

    override fun getCookieIndex(host: String): Flow<List<CookieIndex>> {
        return database.cookie_indicesQueries.getCookieIndex(host).subscribeToOne()
    }

    override suspend fun deleteHost(host: String) {
        database.cookie_indicesQueries.deleteHost(host)
    }
}
