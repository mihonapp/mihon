package eu.kanade.tachiyomi.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.File

/** Reads only the app-selected session store, so updates take effect without restarting extensions. */
internal class DesktopSessionInterceptor(private val file: File?) : Interceptor {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Session(
        val domain: String,
        val cookies: Map<String, String> = emptyMap(),
        val customUserAgent: String? = null,
    )

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(applyTo(chain.request()))

    internal fun applyTo(request: Request): Request {
        val sessions = try {
            if (file == null || !file.isFile || file.length() > 1_048_576) {
                emptyList()
            } else {
                json.decodeFromString<List<Session>>(file.readText())
            }
        } catch (_: Exception) {
            emptyList()
        }
        val selected = sessions.filter {
            val domain = it.domain.trim().lowercase().removePrefix(".")
            domain.isNotBlank() && (request.url.host == domain || request.url.host.endsWith(".$domain"))
        }.maxByOrNull { it.domain.length } ?: return request
        val builder = request.newBuilder()
        // Manual desktop cookies do not carry a Secure flag; never send them over plaintext HTTP.
        if (request.url.isHttps && selected.cookies.isNotEmpty()) {
            val cookies = selected.cookies.toMutableMap()
            request.headers.values("Cookie").flatMap { it.split(';') }.forEach {
                if (it.contains('=')) cookies[it.substringBefore('=').trim()] = it.substringAfter('=').trim()
            }
            builder.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        selected.customUserAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
        return builder.build()
    }
}
