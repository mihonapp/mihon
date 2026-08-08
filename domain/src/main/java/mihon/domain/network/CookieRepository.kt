package mihon.domain.network

interface CookieRepository {
    suspend fun getCookiesForHost(host: String): List<Cookie>
    suspend fun getAllHosts(): List<String>
    suspend fun addOrUpdateCookie(host: String, cookie: Cookie)
    suspend fun deleteCookie(host: String, cookie: Cookie)
    suspend fun deleteHost(host: String)
}
