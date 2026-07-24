package eu.kanade.tachiyomi.network

interface CookieIndexListener {
    suspend fun saveCookieIndex(host: String, key: String, domain: String, path: String)
}
