package mihon.data.network

import eu.kanade.tachiyomi.network.CookieIndexListener
import mihon.domain.network.CookieIndex
import mihon.domain.network.CookieIndexRepository

class CookieIndexListenerImpl(
    private val cookieIndexRepository: CookieIndexRepository,
) : CookieIndexListener {
    override suspend fun saveCookieIndex(host: String, key: String, domain: String, path: String) {
        cookieIndexRepository.updateCookieIndex(host, CookieIndex(key, domain, path), emptyList())
    }
}
