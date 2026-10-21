package mihon.data.network

import android.content.Context
import android.database.SQLException
import android.webkit.CookieManager
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.network.Cookie
import mihon.domain.network.CookieRepository
import tachiyomi.core.common.util.system.logcat
import java.io.File

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CookieRepositoryImpl(
    private val context: Context,
) : CookieRepository {

    private val cookieManager = CookieManager.getInstance()

    private val cookiesFile: File
        get() = File(context.applicationInfo.dataDir, "app_webview/Default/Cookies")

    override suspend fun getCookiesForHost(host: String): List<Cookie> {
        cookieManager.flush()
        return withConnection { conn ->
            conn.prepare(
                "SELECT host_key, name, value, path FROM cookies WHERE host_key = ?1 OR host_key = '.' || ?1",
            ).use { statement ->
                statement.bindText(1, host)
                buildList {
                    while (statement.step()) {
                        add(
                            Cookie(
                                name = statement.getText(1),
                                value = statement.getText(2),
                                path = statement.getText(3),
                                hostOnly = !statement.getText(0).startsWith("."),
                            ),
                        )
                    }
                }
            }
        }.orEmpty()
    }

    override suspend fun getAllHosts(): List<String> {
        cookieManager.flush()
        return withConnection { conn ->
            conn.prepare("SELECT DISTINCT ltrim(host_key, '.') FROM cookies").use { stmt ->
                buildList { while (stmt.step()) add(stmt.getText(0)) }
            }
        }.orEmpty()
    }

    override suspend fun addOrUpdateCookie(host: String, cookie: Cookie) {
        cookieManager.setCookie(buildUrl(host), cookie.string(host))
        cookieManager.flush()
    }

    override suspend fun deleteCookie(host: String, cookie: Cookie) {
        cookieManager.setCookie(buildUrl(host), cookie.deleteString(host))
        cookieManager.flush()
    }

    override suspend fun deleteHost(host: String) {
        val cookies = getCookiesForHost(host)
        cookies.forEach { cookie ->
            cookieManager.setCookie(buildUrl(host), cookie.deleteString(host))
        }
        cookieManager.flush()
    }

    private suspend fun <T> withConnection(block: (SQLiteConnection) -> T): T? =
        withContext(Dispatchers.IO) {
            if (!cookiesFile.exists()) {
                cookieManager.setCookie("http://localhost", "init=true")
                cookieManager.flush()

                cookieManager.setCookie("http://localhost", "init=true; Max-Age=0")
                cookieManager.flush()
                return@withContext null
            }
            try {
                BundledSQLiteDriver()
                    .open(cookiesFile.path, SQLITE_OPEN_READONLY)
                    .use(block)
            } catch (e: SQLException) {
                logcat(LogPriority.ERROR, e) { "Failed to read WebView cookie database" }
                null
            }
        }

    private fun buildUrl(host: String): String = "https://$host/"

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
