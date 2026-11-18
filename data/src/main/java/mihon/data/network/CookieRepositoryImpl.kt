package mihon.data.network

import android.content.Context
import android.webkit.CookieManager
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
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
import mihon.domain.network.SameSite
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CookieRepositoryImpl(
    private val context: Context,
) : CookieRepository {

    private val cookieManager by lazy { CookieManager.getInstance() }

    private val cookiesFile: File
        get() = File(context.applicationInfo.dataDir, "app_webview/Default/Cookies")

    override suspend fun getCookiesForHost(host: String): List<Cookie> {
        return withConnection { conn ->
            conn.prepare(
                """
                SELECT host_key, name, value, path, expires_utc, has_expires, is_secure, is_httponly, samesite
                FROM cookies
                WHERE host_key = ?1 OR host_key = '.' || ?1
                """.trimIndent(),
            ).use { statement ->
                statement.bindText(1, host)
                buildList {
                    while (statement.step()) {
                        val hostKey = statement.getText(0)
                        add(
                            Cookie(
                                name = statement.getText(1),
                                value = statement.getText(2),
                                path = statement.getText(3),
                                hostOnly = !hostKey.startsWith("."),
                                expiresAt = statement.getLong(4).takeIf { statement.getLong(5) != 0L }?.toInstant(),
                                secure = statement.getLong(6) != 0L,
                                httpOnly = statement.getLong(7) != 0L,
                                sameSite = SameSite.fromDatabaseValue(statement.getLong(8)),
                            ),
                        )
                    }
                }
            }
        }.orEmpty()
    }

    override suspend fun getAllHosts(): List<String> {
        return withConnection { conn ->
            conn.prepare("SELECT DISTINCT ltrim(host_key, '.') FROM cookies").use { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }.orEmpty()
    }

    override suspend fun addOrUpdateCookie(host: String, cookie: Cookie) = withContext(Dispatchers.IO) {
        cookieManager.setCookie(buildUrl(host), cookie.toSetCookie(host))
        cookieManager.flush()
    }

    override suspend fun addOrUpdateCookies(cookiesByHost: Map<String, List<Cookie>>) = withContext(Dispatchers.IO) {
        cookiesByHost.forEach { (host, cookies) ->
            val url = buildUrl(host)
            cookies.forEach { cookieManager.setCookie(url, it.toSetCookie(host)) }
        }
        cookieManager.flush()
    }

    override suspend fun deleteCookie(host: String, cookie: Cookie) = withContext(Dispatchers.IO) {
        cookieManager.setCookie(buildUrl(host), cookie.toDeleteCookie(host))
        cookieManager.flush()
    }

    override suspend fun deleteHost(host: String) = withContext(Dispatchers.IO) {
        getCookiesForHost(host).forEach { cookie ->
            cookieManager.setCookie(buildUrl(host), cookie.toDeleteCookie(host))
        }
        cookieManager.flush()
    }

    private suspend fun <T> withConnection(block: (SQLiteConnection) -> T): T? =
        withContext(Dispatchers.IO) {
            cookieManager.flush()

            // Should ideally never run; Just in case
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
            } catch (e: SQLiteException) {
                logcat(LogPriority.ERROR, e) { "Failed to read WebView cookie database" }
                null
            }
        }

    private fun buildUrl(host: String): String = "https://$host/"

    private fun Cookie.toSetCookie(host: String) = buildString {
        append("$name=$value")
        if (!hostOnly) append("; Domain=$host")
        append("; Path=$path")
        expiresAt?.let { append("; Expires=${it.toHttpDate()}") }
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
        when (sameSite) {
            SameSite.None -> append("; SameSite=None")
            SameSite.Lax -> append("; SameSite=Lax")
            SameSite.Strict -> append("; SameSite=Strict")
            SameSite.Unspecified -> {}
        }
    }

    private fun Cookie.toDeleteCookie(host: String) = buildString {
        append("$name=; Max-Age=0")
        if (!hostOnly) append("; Domain=$host")
        append("; Path=$path")
    }
}

// WebView stores expiry as microseconds since 1601-01-01, while `Instant` counts from 1970-01-01.
private const val MICROS_PER_SECOND = 1_000_000L
private const val WINDOWS_TO_UNIX_EPOCH_SECONDS = 11_644_473_600L

private fun Long.toInstant(): Instant =
    Instant.fromEpochSeconds(this / MICROS_PER_SECOND - WINDOWS_TO_UNIX_EPOCH_SECONDS)

private fun Instant.toHttpDate(): String =
    DateTimeFormatter.RFC_1123_DATE_TIME.format(toJavaInstant().atOffset(ZoneOffset.UTC))
