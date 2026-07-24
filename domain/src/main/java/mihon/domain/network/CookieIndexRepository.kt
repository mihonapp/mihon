package mihon.domain.network

import kotlinx.coroutines.flow.Flow

interface CookieIndexRepository {
    suspend fun insertHost(host: String)
    suspend fun updateCookieIndex(host: String, cookieIndex: CookieIndex?, toRemove: List<CookieIndex>)
    fun getHosts(): Flow<List<String>>
    fun getCookieIndex(host: String): Flow<List<CookieIndex>>

    suspend fun deleteHost(host: String)
}
