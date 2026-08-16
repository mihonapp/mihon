package mihon.data.network

import android.content.Context
import android.webkit.CookieManager
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.File
import mihon.data.CookiesDatabase
import mihon.domain.network.Cookie
import mihon.domain.network.CookieRepository
import java.io.File

class CookieRepositoryImpl(
    private val context: Context,
) : CookieRepository {

    private val cookieManager = CookieManager.getInstance()

    private val database: CookiesDatabase by lazy {
        val cookiesFile = File(context.applicationInfo.dataDir, "app_webview/Default/Cookies")

        if (!cookiesFile.exists()) {
            cookieManager.setCookie("http://localhost", "init=true")
            cookieManager.flush()

            cookieManager.setCookie("http://localhost", "init=true; Max-Age=0")
            cookieManager.flush()
        }

        val driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(cookiesFile),
            schema = CookiesDatabase.Schema,
        )

        CookiesDatabase(driver)
    }

    override suspend fun getCookiesForHost(host: String): List<Cookie> {
        cookieManager.flush()
        return database.cookiesQueries.getCookiesForHost(host) { host, name, value, path ->
            Cookie(
                name = name,
                value = value,
                path = path,
                hostOnly = !host.startsWith("."),
            )
        }.awaitAsList()
    }

    override suspend fun getAllHosts(): List<String> {
        cookieManager.flush()
        return database.cookiesQueries.getAllHosts().awaitAsList()
    }

    override suspend fun addOrUpdateCookie(host: String, cookie: Cookie) {
        cookieManager.setCookie(host, cookie.string(host))
        cookieManager.flush()
    }

    override suspend fun deleteCookie(host: String, cookie: Cookie) {
        cookieManager.setCookie(host, cookie.deleteString(host))
        cookieManager.flush()
    }

    override suspend fun deleteHost(host: String) {
        val cookies = getCookiesForHost(host)
        cookies.forEach { cookie ->
            cookieManager.setCookie(host, cookie.deleteString(host))
        }
        cookieManager.flush()
    }

    private fun Cookie.string(host: String) = buildString {
        append("$name=$value")
        if (!hostOnly) append("; Domain=$host")
        append("; Path=$path")
    }

    private fun Cookie.deleteString(host: String) = buildString {
        append("$name=; Max-Age=0")
        if (!hostOnly) append("; Domain=$host")
        append("; Path=$path")
    }
}
